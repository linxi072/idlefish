package com.idlefish.trade.common.observability;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * 全链路 traceId 上下文（F-12.4）。
 *
 * <p>HTTP 入口由 {@link ObservabilityFilter} 注入 {@code traceId} 到 MDC；
 * 但调度任务（{@code @Scheduled}）、异步线程、线程池、出站 HTTP 调用会脱离该上下文，
 * 导致一条业务链路在日志中被打断。本工具提供：
 * <ul>
 *   <li>{@link #ensure()}：确保当前线程存在 traceId（缺失则生成），供调度/异步任务入口调用；</li>
 *   <li>{@link #wrap(Runnable)} / {@link #wrap(Callable)}：捕获当前 MDC 并在子线程恢复，实现跨线程透传；</li>
 *   <li>{@link #HEADER}：出站 HTTP 头常量，配合 {@link TraceRestTemplateInterceptor} 透传至下游。</li>
 * </ul>
 */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceContext() {
    }

    /** 当前 traceId（可能为 null）。 */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /**
     * 确保存在 traceId：缺失则生成并写入 MDC，返回当前（或新建）的 traceId。
     * 调度任务 / 手动线程入口应首先调用，使该次执行的日志可被统一追踪。
     */
    public static String ensure() {
        String t = MDC.get(MDC_KEY);
        if (t == null || t.isBlank()) {
            t = newTraceId();
            MDC.put(MDC_KEY, t);
        }
        return t;
    }

    /** 生成新的 traceId。 */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 捕获当前 MDC 上下文，返回可在子线程中恢复并自动还原父线程上下文的 Runnable。 */
    public static Runnable wrap(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            apply(captured);
            try {
                task.run();
            } finally {
                apply(previous);
            }
        };
    }

    /** 捕获当前 MDC 上下文，返回可在子线程中恢复并自动还原父线程上下文的 Callable。 */
    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            apply(captured);
            try {
                return task.call();
            } finally {
                apply(previous);
            }
        };
    }

    private static void apply(Map<String, String> ctx) {
        if (ctx != null) {
            MDC.setContextMap(ctx);
        } else {
            MDC.clear();
        }
    }
}
