package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阈值告警引擎单测（F-12.3）：慢请求 / 错误率 / 验签失败 / 冷却 / 开关 六类场景。
 * 用真实 MetricsRegistry 实例驱动，纯 Mockito，离线可跑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertEvaluatorTest {

    private MetricsRegistry metrics;
    private NotificationService notificationService;
    private IdlefishProperties props;
    private AlertEvaluator evaluator;

    @BeforeEach
    void setup() {
        metrics = new MetricsRegistry();
        notificationService = mock(NotificationService.class);
        props = new IdlefishProperties();
        evaluator = new AlertEvaluator(metrics, notificationService, props);
    }

    @Test
    void slow_request_triggers_alert() {
        metrics.record("http.latency./api/x", 2000);
        metrics.increment("http.request./api/x");
        evaluator.evaluate();
        verify(notificationService).notify(ArgumentMatchers.eq(1L), ArgumentMatchers.eq(NotificationType.SYSTEM_ALERT),
                ArgumentMatchers.eq("alert:slow_request"), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void normal_no_alert() {
        metrics.record("http.latency./api/x", 100);
        metrics.increment("http.request./api/x");
        evaluator.evaluate();
        verify(notificationService, never()).notify(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void error_rate_triggers_alert() {
        metrics.increment("http.request./a");
        metrics.increment("http.status.5xx");
        evaluator.evaluate();
        verify(notificationService).notify(ArgumentMatchers.eq(1L), ArgumentMatchers.eq(NotificationType.SYSTEM_ALERT),
                ArgumentMatchers.eq("alert:error_rate"), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void verify_failure_triggers_alert() {
        for (int i = 0; i < 10; i++) {
            metrics.increment("pay.notify.v3.failure");
        }
        evaluator.evaluate();
        verify(notificationService).notify(ArgumentMatchers.eq(1L), ArgumentMatchers.eq(NotificationType.SYSTEM_ALERT),
                ArgumentMatchers.eq("alert:verify_failure"), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void cooldown_prevents_duplicate() {
        metrics.record("http.latency./api/x", 2000);
        metrics.increment("http.request./api/x");
        evaluator.evaluate();
        evaluator.evaluate(); // 冷却期内
        verify(notificationService, times(1)).notify(ArgumentMatchers.eq(1L), ArgumentMatchers.eq(NotificationType.SYSTEM_ALERT),
                ArgumentMatchers.eq("alert:slow_request"), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void disabled_no_alert() {
        props.getObservability().setAlertEnabled(false);
        metrics.record("http.latency./api/x", 2000);
        evaluator.evaluate();
        verify(notificationService, never()).notify(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }
}
