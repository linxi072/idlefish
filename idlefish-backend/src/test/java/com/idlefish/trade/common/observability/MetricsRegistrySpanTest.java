package com.idlefish.trade.common.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MetricsRegistry 结构化 span 单测（T01，REQ-02/REQ-04）：校验 timed() 输出的 JSON span
 * 正确透传 MDC 的 orderId / bizNo，并按成功/失败填充 result。
 * 通过 logback ListAppender 捕获 INFO 级 span 日志，离线可跑。
 */
class MetricsRegistrySpanTest {

    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;
    private Logger metricsLogger;

    @BeforeEach
    void setup() {
        metricsLogger = (Logger) LoggerFactory.getLogger(MetricsRegistry.class);
        previousLevel = metricsLogger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        metricsLogger.addAppender(appender);
        metricsLogger.setLevel(Level.INFO);
        MDC.clear();
    }

    @AfterEach
    void teardown() {
        metricsLogger.detachAppender(appender);
        metricsLogger.setLevel(previousLevel);
        MDC.clear();
    }

    private String lastSpan() {
        assertTrue(appender.list.size() >= 1, "未捕获到 span 日志");
        return appender.list.get(appender.list.size() - 1).getFormattedMessage();
    }

    @Test
    void span_includesOrderIdAndBizNoFromMdc() {
        MDC.put(TraceContext.ORDER_ID_KEY, "O-1001");
        MDC.put(TraceContext.BIZ_NO_KEY, "WD-2002");
        MetricsRegistry metrics = new MetricsRegistry();
        metrics.timed("withdrawal.transfer", () -> "ok");

        String span = lastSpan();
        assertTrue(span.contains("\"action\":\"withdrawal.transfer\""), "action 缺失: " + span);
        assertTrue(span.contains("\"result\":\"success\""), "result 缺失: " + span);
        assertTrue(span.contains("\"orderId\":\"O-1001\""), "orderId 未透传: " + span);
        assertTrue(span.contains("\"bizNo\":\"WD-2002\""), "bizNo 未透传: " + span);
        assertTrue(span.contains("\"costMs\""), "costMs 缺失: " + span);
    }

    @Test
    void span_omitsOrderIdBizNoWhenAbsent() {
        MetricsRegistry metrics = new MetricsRegistry();
        metrics.timed("pay.notify", () -> 1);
        String span = lastSpan();
        assertFalse(span.contains("orderId"), "未注入不应出现 orderId: " + span);
        assertFalse(span.contains("bizNo"), "未注入不应出现 bizNo: " + span);
        assertTrue(span.contains("\"result\":\"success\""));
    }

    @Test
    void span_failureResultOnException() {
        MetricsRegistry metrics = new MetricsRegistry();
        boolean threw = false;
        try {
            metrics.timed("pay.notify.v3", () -> {
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException e) {
            threw = true;
        }
        assertTrue(threw);
        String span = lastSpan();
        assertTrue(span.contains("\"action\":\"pay.notify.v3\""), span);
        assertTrue(span.contains("\"result\":\"failure\""), "异常应记 failure: " + span);
        // 失败时仍应记录耗时
        assertTrue(span.contains("\"costMs\""));
    }

    @Test
    void span_blankOrderIdSkipped() {
        // 仅空白 bizNo，不应输出 bizNo 字段
        MDC.put(TraceContext.BIZ_NO_KEY, "   ");
        MetricsRegistry metrics = new MetricsRegistry();
        metrics.timed("x", () -> null);
        String span = lastSpan();
        assertFalse(span.contains("bizNo"), "空白 bizNo 不应输出: " + span);
    }
}
