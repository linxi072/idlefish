package com.idlefish.trade.event;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.observability.TraceContext;
import com.idlefish.trade.event.mapper.EventOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 事件分发器（ITER-T06 核心）：Outbox 行 → 匹配的本地监听器 → 状态回写。
 * <p>
 * 三条链路共用本组件，保证「分发语义 + 重试/死信 + 指标」只有一份实现：
 * <ol>
 *   <li>{@link DomainEventPublisher}：业务事务提交后（afterCommit）即时分发；</li>
 *   <li>{@code EventOutboxDispatcher}：定时扫描 PENDING/FAILED 到期行补偿分发（REQ-06）；</li>
 *   <li>回放执行器：从事件存储重建 Outbox 行，以 {@code replay=true} 分发（REQ-13）。</li>
 * </ol>
 * <p>单条事件的状态回写独立提交（无外层事务）：分发结果不随业务事务回滚，
 * 失败行由定时补偿重试，重试耗尽转 DEAD（REQ-08 死信）。
 * MDC 在分发线程注入事件 traceId（REQ-10），结束后恢复。</p>
 */
@Slf4j
@Component
public class EventDispatcher {

    /** lastError 截断长度（与 DDL VARCHAR(500) 对齐）。 */
    private static final int MAX_ERROR_LEN = 500;

    private final List<DomainEventListener> listeners;
    private final EventOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metrics;
    private final IdlefishProperties props;
    /** 可选 MQ 镜像（本地消费成功后投递，best-effort；未启用时为 no-op）。 */
    private final EventMqTransport mqTransport;

    public EventDispatcher(List<DomainEventListener> listeners,
                           EventOutboxMapper outboxMapper,
                           ObjectMapper objectMapper,
                           MetricsRegistry metrics,
                           IdlefishProperties props,
                           EventMqTransport mqTransport) {
        this.listeners = listeners;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.props = props;
        this.mqTransport = mqTransport;
    }

    /**
     * 分发单条 Outbox 行（含状态回写）。
     *
     * @param row    Outbox 行（事实源）
     * @param replay 是否回放流量（REQ-13：true 时副作用监听器跳过）
     * @return 分发结果：全部匹配监听器消费成功（或无可匹配监听器）时 {@code ok=true}
     */
    public DispatchResult dispatch(EventOutbox row, boolean replay) {
        EventType type = EventType.fromCode(row.getEventType());
        if (type == null) {
            // 未知类型直接死信（REQ-02 schema 演进：老版本代码不消费新类型，留待升级）
            markDead(row, "unknown event type: " + row.getEventType());
            return new DispatchResult(false, 0, 0);
        }

        DomainEvent event = toDomainEvent(row, type, replay);
        List<DomainEventListener> matched = new ArrayList<>();
        List<DomainEventListener> skipped = new ArrayList<>();
        for (DomainEventListener l : listeners) {
            if (l.interests() == null || !l.interests().contains(type)) {
                continue;
            }
            if (replay && l.hasSideEffect()) {
                skipped.add(l);
                continue;
            }
            matched.add(l);
        }

        // 注入事件链路上下文（REQ-10）：分发线程日志可按 traceId 关联发布链路
        String prevTrace = MDC.get(TraceContext.MDC_KEY);
        String prevOrder = MDC.get(TraceContext.ORDER_ID_KEY);
        try {
            if (event.getTraceId() != null && !event.getTraceId().isBlank()) {
                MDC.put(TraceContext.MDC_KEY, event.getTraceId());
            }
            MDC.put(TraceContext.ORDER_ID_KEY, row.getAggregateId());

            String failure = null;
            for (DomainEventListener l : matched) {
                try {
                    l.onEvent(event);
                } catch (RuntimeException e) {
                    log.warn("[event] listener {} consume failed, eventId={}, type={}",
                            l.getClass().getSimpleName(), row.getEventId(), row.getEventType(), e);
                    failure = l.getClass().getSimpleName() + ": " + e.getMessage();
                    break; // 首败即停：全部监听器随下次重试整体重放（消费幂等保证重放安全）
                }
            }

            if (failure == null) {
                markSent(row);
                metrics.increment("event.dispatch.success");
                if (!skipped.isEmpty()) {
                    metrics.increment("event.dispatch.replaySkipped");
                    log.info("[event] replay skipped side-effect listeners, count={}, eventId={}",
                            skipped.size(), row.getEventId());
                }
                // 可选 MQ 镜像：仅首次成功分发投递（回放流量不重复外发，REQ-13）
                if (!replay) {
                    mqTransport.send(event, event.getPayloadJson());
                }
                return new DispatchResult(true, matched.size(), skipped.size());
            }
            markFailed(row, failure);
            metrics.increment("event.dispatch.failure");
            return new DispatchResult(false, matched.size(), skipped.size());
        } finally {
            restore(prevTrace, prevOrder);
        }
    }

    /** 指数退避：第 n 次重试延迟 = base * 2^n，封顶 maxBackoffSeconds。 */
    public long backoffSeconds(int retryCount) {
        IdlefishProperties.Event cfg = props.getEvent();
        long base = Math.max(1L, cfg.getBaseBackoffSeconds());
        long cap = Math.max(base, cfg.getMaxBackoffSeconds());
        long delay = base;
        for (int i = 0; i < Math.min(retryCount, 16); i++) {
            delay *= 2;
            if (delay >= cap) {
                return cap;
            }
        }
        return Math.min(delay, cap);
    }

    // ===== 分发结果 =====

    /** 单条事件的一次分发结果（ok / 匹配监听器数 / 被跳过的副作用监听器数）。 */
    public static class DispatchResult {
        private final boolean ok;
        private final int matched;
        private final int skipped;

        public DispatchResult(boolean ok, int matched, int skipped) {
            this.ok = ok;
            this.matched = matched;
            this.skipped = skipped;
        }

        public boolean isOk() {
            return ok;
        }

        public int getMatched() {
            return matched;
        }

        public int getSkipped() {
            return skipped;
        }
    }

    // ===== 状态回写 =====

    private void markSent(EventOutbox row) {
        outboxMapper.update(null, new LambdaUpdateWrapper<EventOutbox>()
                .eq(EventOutbox::getId, row.getId())
                .set(EventOutbox::getStatus, EventOutboxStatus.SENT.name())
                .set(EventOutbox::getUpdatedAt, LocalDateTime.now()));
        row.setStatus(EventOutboxStatus.SENT.name());
    }

    private void markFailed(EventOutbox row, String error) {
        int retryCount = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
        int maxRetry = Math.max(1, props.getEvent().getMaxRetry());
        if (retryCount >= maxRetry) {
            markDead(row, "retry exhausted(" + retryCount + "): " + error);
            return;
        }
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(backoffSeconds(retryCount));
        outboxMapper.update(null, new LambdaUpdateWrapper<EventOutbox>()
                .eq(EventOutbox::getId, row.getId())
                .set(EventOutbox::getStatus, EventOutboxStatus.FAILED.name())
                .set(EventOutbox::getRetryCount, retryCount)
                .set(EventOutbox::getNextRetryAt, nextRetryAt)
                .set(EventOutbox::getLastError, truncate(error))
                .set(EventOutbox::getUpdatedAt, LocalDateTime.now()));
        row.setStatus(EventOutboxStatus.FAILED.name());
        row.setRetryCount(retryCount);
        row.setNextRetryAt(nextRetryAt);
    }

    private void markDead(EventOutbox row, String error) {
        outboxMapper.update(null, new LambdaUpdateWrapper<EventOutbox>()
                .eq(EventOutbox::getId, row.getId())
                .set(EventOutbox::getStatus, EventOutboxStatus.DEAD.name())
                .set(EventOutbox::getLastError, truncate(error))
                .set(EventOutbox::getUpdatedAt, LocalDateTime.now()));
        row.setStatus(EventOutboxStatus.DEAD.name());
        metrics.increment("event.dead.letter");
        log.error("[event] dead letter, eventId={}, type={}, aggregateId={}, reason={}",
                row.getEventId(), row.getEventType(), row.getAggregateId(), error);
    }

    // ===== 辅助 =====

    /** Outbox 行 → 领域事件信封（负载 JSON 反序列化为 Map/List；空负载为 null）。 */
    private DomainEvent toDomainEvent(EventOutbox row, EventType type, boolean replay) {
        DomainEvent event = new DomainEvent();
        event.setEventId(row.getEventId());
        event.setEventType(type.name());
        event.setVersion(row.getVersion() == null ? type.getVersion() : row.getVersion());
        event.setOccurredAt(row.getCreatedAt());
        event.setAggregateId(row.getAggregateId());
        event.setTraceId(row.getTraceId());
        event.setPayloadJson(row.getPayload());
        event.setReplay(replay);
        String json = row.getPayload();
        if (json != null && !json.isBlank()) {
            try {
                event.setPayload(objectMapper.readValue(json, Object.class));
            } catch (Exception e) {
                log.warn("[event] payload deserialize failed, eventId={}, fallback to raw string",
                        row.getEventId(), e);
                event.setPayload(json);
            }
        }
        return event;
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN);
    }

    private void restore(String prevTrace, String prevOrder) {
        if (prevTrace != null) {
            MDC.put(TraceContext.MDC_KEY, prevTrace);
        } else {
            MDC.remove(TraceContext.MDC_KEY);
        }
        if (prevOrder != null) {
            MDC.put(TraceContext.ORDER_ID_KEY, prevOrder);
        } else {
            MDC.remove(TraceContext.ORDER_ID_KEY);
        }
    }
}
