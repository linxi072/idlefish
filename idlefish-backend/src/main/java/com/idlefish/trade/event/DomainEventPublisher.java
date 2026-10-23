package com.idlefish.trade.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.observability.TraceContext;
import com.idlefish.trade.event.mapper.EventOutboxMapper;
import com.idlefish.trade.event.mapper.EventRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 领域事件发布器（ITER-T06 / REQ-05 Outbox 模式）。
 * <p>
 * <b>不丢事件的关键设计</b>：事件行与业务数据在<b>同一数据库事务</b>内写入 Outbox，
 * 业务提交则事件必然落库；提交后经 {@code TransactionSynchronization#afterCommit}
 * 在同线程即时分发至本地监听器。分发失败不回滚业务（REQ-03/04 事件 best-effort、
 * 不影响主流程），由定时补偿重试直至成功或死信。
 * <ul>
 *   <li>存在事务：插入 Outbox + 事件存储（同事务），注册 afterCommit 分发；</li>
 *   <li>无事务（调度任务等）：直接插入并同步分发，语义等价；</li>
 *   <li>发布动作本身异常被捕获并打点 {@code event.publish.failure}，绝不打断业务链路。</li>
 * </ul>
 * <p>使用范式（业务服务内）：</p>
 * <pre>{@code
 * publisher.publish(EventType.ORDER_SHIPPED, order.getOrderNo(),
 *         Map.of("buyerId", order.getBuyerId(), "amountFen", order.getAmountFen()));
 * }</pre>
 */
@Slf4j
@Service
public class DomainEventPublisher {

    private final EventOutboxMapper outboxMapper;
    private final EventRecordMapper recordMapper;
    private final EventDispatcher dispatcher;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metrics;
    private final IdlefishProperties props;

    public DomainEventPublisher(EventOutboxMapper outboxMapper,
                                EventRecordMapper recordMapper,
                                EventDispatcher dispatcher,
                                ObjectMapper objectMapper,
                                MetricsRegistry metrics,
                                IdlefishProperties props) {
        this.outboxMapper = outboxMapper;
        this.recordMapper = recordMapper;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.props = props;
    }

    /**
     * 发布领域事件（负载为对象，自动序列化为 JSON）。
     *
     * @param type        事件类型
     * @param aggregateId 聚合根标识（订单号 / 支付单号 / 商品 ID 等）
     * @param payload     业务负载（金额字段单位「分」；可为 null）
     */
    public void publish(EventType type, String aggregateId, Object payload) {
        publish(type, aggregateId, payload, null);
    }

    /**
     * 发布领域事件（可显式指定 traceId；缺省取当前 MDC）。
     */
    public void publish(EventType type, String aggregateId, Object payload, String traceId) {
        metrics.increment("event.publish.total");
        if (!props.getEvent().isEnabled()) {
            log.info("[event] disabled, drop event type={}, aggregateId={}", type, aggregateId);
            metrics.increment("event.publish.disabled");
            return;
        }
        try {
            doPublish(type, aggregateId, payload, traceId);
            metrics.increment("event.publish." + type.name());
        } catch (RuntimeException e) {
            // REQ-03/04：事件发布失败绝不打断业务主流程；打点 + 结构化日志供告警
            metrics.increment("event.publish.failure");
            log.error("[event] publish failed, type={}, aggregateId={}", type, aggregateId, e);
        }
    }

    private void doPublish(EventType type, String aggregateId, Object payload, String traceId) {
        String eventId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        String tid = (traceId != null && !traceId.isBlank()) ? traceId
                : (TraceContext.current() != null ? TraceContext.current() : TraceContext.ensure());
        String payloadJson = toJson(payload);

        EventOutbox outbox = new EventOutbox();
        outbox.setEventId(eventId);
        outbox.setEventType(type.name());
        outbox.setVersion(type.getVersion());
        outbox.setAggregateId(aggregateId);
        outbox.setTraceId(tid);
        outbox.setPayload(payloadJson);
        outbox.setStatus(EventOutboxStatus.PENDING.name());
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(now);

        EventRecord record = null;
        if (type.isPersisted()) {
            record = new EventRecord();
            record.setEventId(eventId);
            record.setEventType(type.name());
            record.setVersion(type.getVersion());
            record.setAggregateId(aggregateId);
            record.setTraceId(tid);
            record.setPayload(payloadJson);
            record.setOccurredAt(now);
        }

        boolean syncActive = TransactionSynchronizationManager.isSynchronizationActive();
        if (syncActive) {
            // REQ-05：与业务同事务落库——业务提交则事件必然存在
            outboxMapper.insert(outbox);
            if (record != null) {
                recordMapper.insert(record);
            }
            EventOutbox outboxRef = outbox;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeDispatch(outboxRef, false);
                }
            });
        } else {
            // 无事务上下文（@Scheduled 等）：直接落库并同步分发
            outboxMapper.insert(outbox);
            if (record != null) {
                recordMapper.insert(record);
            }
            safeDispatch(outbox, false);
        }
        log.info("[event] published, eventId={}, type={}, aggregateId={}, txActive={}",
                eventId, type, aggregateId, syncActive);
    }

    /** 提交后 / 无事务场景的分发兜底：任何异常只记日志，不影响调用线程。 */
    private void safeDispatch(EventOutbox outbox, boolean replay) {
        try {
            dispatcher.dispatch(outbox, replay);
        } catch (RuntimeException e) {
            // 分发器内部已兜底各类异常；此处防御状态回写本身的 DB 故障——
            // 行仍为 PENDING，定时补偿（REQ-06）会再次尝试
            log.error("[event] after-commit dispatch crashed, will retry by scheduler, eventId={}",
                    outbox.getEventId(), e);
        }
    }

    /**
     * 空值安全负载构建器：{@link java.util.Map#of} 遇 null 值直接 NPE 且发生在调用侧
     * （publish 的 try/catch 保护不到），事件负载一律经此构建——null 值键被静默跳过，
     * 保证事件发布永不打断业务主流程（REQ-03/04）。
     */
    public static java.util.Map<String, Object> payload(Object... kv) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v != null) {
                m.put(String.valueOf(kv[i]), v);
            }
        }
        return m;
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("event payload serialize failed", e);
        }
    }
}
