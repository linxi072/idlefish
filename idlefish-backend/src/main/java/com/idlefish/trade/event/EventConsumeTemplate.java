package com.idlefish.trade.event;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.idempotent.IdempotentService;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.observability.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * 事件消费模板（ITER-T06 / REQ-07 消费幂等 100% + REQ-10 traceId 注入）。
 * <p>
 * 监听器内部统一经本模板执行消费逻辑，获得三项保证：
 * <ol>
 *   <li><b>消费幂等（REQ-07）</b>：以 {@code eventId + 监听器类名} 为幂等键，
 *       经 {@link IdempotentService} 独立事务三态占用；重试重放命中 SUCCESS 直接跳过，
 *       绝不重复执行副作用；并发重复消费（BIZ_PROCESSING）按失败上抛，随下次重试自然收敛；</li>
 *   <li><b>traceId 跨异步注入（REQ-10）</b>：消费线程 MDC 注入事件发布时的 traceId，
 *       日志可按链路串联；结束后恢复原值；</li>
 *   <li><b>指标</b>：{@code event.consume.*} 计数（success / duplicate / failure）。</li>
 * </ol>
 * <p>使用范式（监听器内）：</p>
 * <pre>{@code
 * public void onEvent(DomainEvent event) {
 *     consumeTemplate.execute(event, () -> {
 *         // 业务副作用：可能被重试重放，模板保证只执行一次
 *     });
 * }
 * }</pre>
 */
@Slf4j
@Service
public class EventConsumeTemplate {

    /** 消费成功标记（回放反序列化安全：非 null 结果才能被 SUCCESS 语义识别）。 */
    private static final String CONSUME_MARKER = "OK";

    /** PROCESSING 占用过期时间（秒）：超过视为消费实例崩溃，允许接管重试。 */
    private static final long CONSUME_TTL_SECONDS = 300L;

    private final IdempotentService idempotentService;
    private final MetricsRegistry metrics;

    public EventConsumeTemplate(IdempotentService idempotentService, MetricsRegistry metrics) {
        this.idempotentService = idempotentService;
        this.metrics = metrics;
    }

    /**
     * 幂等消费事件（事件消费均为无返回值副作用场景）。
     *
     * @param event    领域事件（eventId 为幂等键来源）
     * @param consumer 消费逻辑（在幂等保护与 traceId 上下文中执行；只执行一次）
     */
    public void execute(DomainEvent event, Runnable consumer) {
        String bizType = "event.consume." + event.getEventType();
        String bizKey = event.getEventId() + ":" + currentListenerTag();

        String prevTrace = MDC.get(TraceContext.MDC_KEY);
        try {
            if (event.getTraceId() != null && !event.getTraceId().isBlank()) {
                MDC.put(TraceContext.MDC_KEY, event.getTraceId());
            }
            idempotentService.execute(bizType, bizKey, CONSUME_TTL_SECONDS, String.class, () -> {
                consumer.run();
                return CONSUME_MARKER;
            });
            metrics.increment("event.consume.success");
        } catch (BizException e) {
            if (e.getCode() == Code.BIZ_ERROR.getCode()) {
                // 幂等记录 SUCCESS 但结果快照缺失等边缘态：事件已消费过，静默跳过（REQ-07 不重复执行）
                metrics.increment("event.consume.duplicate");
                log.info("[event] already consumed, skip, eventId={}, key={}", event.getEventId(), bizKey);
                return;
            }
            // BIZ_PROCESSING（并发重复）等：按消费失败上抛，随重试收敛
            metrics.increment("event.consume.failure");
            throw e;
        } catch (RuntimeException e) {
            metrics.increment("event.consume.failure");
            throw e;
        } finally {
            if (prevTrace != null) {
                MDC.put(TraceContext.MDC_KEY, prevTrace);
            } else {
                MDC.remove(TraceContext.MDC_KEY);
            }
        }
    }

    /** 从调用栈定位监听器类名（幂等键隔离不同监听器对同一事件的消费）。 */
    private String currentListenerTag() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            Class<?> cls = safeClass(e.getClassName());
            if (cls != null && DomainEventListener.class.isAssignableFrom(cls)
                    && !cls.isInterface()) {
                return cls.getSimpleName();
            }
        }
        return "unknown";
    }

    private Class<?> safeClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }
}
