package com.idlefish.trade.common.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MetricsRegistry 生产化增强单测（T01）：
 * <ul>
 *   <li>有界蓄水池分位数（p50/p95/p99）正确性与单调性；</li>
 *   <li>timed/timedRun 输出结构化 JSON span 日志（REQ-04），且成功/失败计数契约不变。</li>
 * </ul>
 * 纯 Mockito/零依赖，离线可跑。
 */
class MetricsRegistryPercentileTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> timerOf(MetricsRegistry metrics, String name) {
        Map<String, Object> snap = metrics.snapshot();
        List<Map<String, Object>> timers = (List<Map<String, Object>>) snap.get("timers");
        return timers.stream().filter(m -> name.equals(m.get("name"))).findFirst().orElseThrow();
    }

    @Test
    void percentiles_match_nearest_rank() {
        MetricsRegistry metrics = new MetricsRegistry();
        for (int i = 1; i <= 100; i++) {
            metrics.record("latency.test", i);
        }
        Map<String, Object> t = timerOf(metrics, "latency.test");
        assertEquals(100L, t.get("count"));
        assertEquals(100L, t.get("maxMs"));
        // nearest-rank 分位数：rank = ceil(p*n)-1
        assertEquals(50L, t.get("p50Ms"));
        assertEquals(95L, t.get("p95Ms"));
        assertEquals(99L, t.get("p99Ms"));
    }

    @Test
    void percentiles_monotonic_and_bounded() {
        MetricsRegistry metrics = new MetricsRegistry();
        for (int i = 0; i < 2000; i++) {
            metrics.record("latency.big", i);
        }
        Map<String, Object> t = timerOf(metrics, "latency.big");
        long p50 = ((Number) t.get("p50Ms")).longValue();
        long p95 = ((Number) t.get("p95Ms")).longValue();
        long p99 = ((Number) t.get("p99Ms")).longValue();
        assertTrue(p50 <= p95, "p50 应 <= p95");
        assertTrue(p95 <= p99, "p95 应 <= p99");
        assertTrue(p99 <= 1999, "p99 不应超过样本最大值");
        assertTrue(p50 >= 0, "分位数不应为负");
    }

    @Test
    void empty_metrics_have_no_timers() {
        MetricsRegistry metrics = new MetricsRegistry();
        Map<String, Object> snap = metrics.snapshot();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timers = (List<Map<String, Object>>) snap.get("timers");
        assertTrue(timers.isEmpty(), "无任何记录时不应有定时器");
    }

    @Test
    void timed_emits_structured_span_log_on_success() {
        Logger logger = (Logger) LoggerFactory.getLogger(MetricsRegistry.class);
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MetricsRegistry metrics = new MetricsRegistry();
            String result = metrics.timed("test.action", () -> "ok");

            assertEquals("ok", result);
            assertEquals(1L, metrics.counter("test.action.success"));

            boolean spanLogged = appender.list.stream().anyMatch(e ->
                    e.getMessage().contains("\"action\":\"test.action\"")
                            && e.getMessage().contains("\"result\":\"success\"")
                            && e.getMessage().contains("\"costMs\"")
                            && e.getMessage().contains("\"traceId\""));
            assertTrue(spanLogged, "应输出含 traceId/action/result/costMs 的结构化 span 日志");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(null);
        }
    }

    @Test
    void timed_emits_span_log_on_failure_and_counts_failure() {
        Logger logger = (Logger) LoggerFactory.getLogger(MetricsRegistry.class);
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MetricsRegistry metrics = new MetricsRegistry();
            RuntimeException ex = new RuntimeException("boom");
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> metrics.timed("test.action2", () -> {
                        throw ex;
                    }));
            assertEquals(ex, thrown);
            assertEquals(1L, metrics.counter("test.action2.failure"));

            boolean spanLogged = appender.list.stream().anyMatch(e ->
                    e.getMessage().contains("\"action\":\"test.action2\"")
                            && e.getMessage().contains("\"result\":\"failure\""));
            assertTrue(spanLogged, "失败路径仍应输出结构化 span 日志");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(null);
        }
    }

    @Test
    void timedRun_emits_span_and_counts() {
        Logger logger = (Logger) LoggerFactory.getLogger(MetricsRegistry.class);
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MetricsRegistry metrics = new MetricsRegistry();
            metrics.timedRun("test.run", () -> {
            });
            assertEquals(1L, metrics.counter("test.run.success"));
            boolean spanLogged = appender.list.stream().anyMatch(e ->
                    e.getMessage().contains("\"action\":\"test.run\"")
                            && e.getMessage().contains("\"result\":\"success\""));
            assertTrue(spanLogged, "timedRun 应输出结构化 span 日志");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(null);
        }
    }
}
