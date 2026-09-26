package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 阈值告警引擎（F-12.3）：周期性从 {@link MetricsRegistry} 读取指标，评估三类异常：
 * <ul>
 *   <li>慢请求：按路由均值的 {@code http.latency.*} 超过 {@code slowRequestMs}</li>
 *   <li>错误率：{@code http.status.5xx} / {@code http.request.*} 超过 {@code errorRateThreshold}</li>
 *   <li>支付验签失败：{@code pay.notify.v3.failure} + {@code pay.notify.verify.failure} 累计超过 {@code verifyFailureThreshold}</li>
 * </ul>
 * 越界则通过 {@link NotificationService} 向管理员发送 {@link NotificationType#SYSTEM_ALERT}（best-effort）。
 * 带冷却去重，避免同一类告警在冷却期内重复轰炸。
 */
@Component
public class AlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluator.class);

    private static final String C_REQUEST = "http.request.";
    private static final String C_STATUS_5XX = "http.status.5xx";
    private static final String C_VERIFY_V3 = "pay.notify.v3.failure";
    private static final String C_VERIFY_FORM = "pay.notify.verify.failure";
    private static final String T_LATENCY = "http.latency.";

    private final MetricsRegistry metrics;
    private final NotificationService notificationService;
    private final IdlefishProperties props;
    private final ConcurrentMap<String, Instant> lastAlerted = new ConcurrentHashMap<>();

    public AlertEvaluator(MetricsRegistry metrics, NotificationService notificationService, IdlefishProperties props) {
        this.metrics = metrics;
        this.notificationService = notificationService;
        this.props = props;
    }

    /** 每 60 秒评估一次；沙箱无 MySQL 上下文时不触发，纯单测直接调用 evaluate()。 */
    @Scheduled(fixedDelay = 60_000)
    public void evaluate() {
        IdlefishProperties.Observability obs = props.getObservability();
        if (obs == null || !obs.isEnabled() || !obs.isAlertEnabled()) {
            return;
        }
        Map<String, Object> snap = metrics.snapshot();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> counters = (List<Map<String, Object>>) snap.get("counters");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timers = (List<Map<String, Object>>) snap.get("timers");

        long totalReq = sumPrefix(counters, C_REQUEST);
        long err5xx = valueOf(counters, C_STATUS_5XX);
        long verifyFail = valueOf(counters, C_VERIFY_V3) + valueOf(counters, C_VERIFY_FORM);
        double errorRate = totalReq > 0 ? (double) err5xx / totalReq : 0d;

        if (errorRate > obs.getErrorRateThreshold()) {
            fire("error_rate", obs, "接口错误率告警",
                    "近周期错误率 " + String.format("%.2f%%", errorRate * 100)
                            + " 超阈值 " + String.format("%.2f%%", obs.getErrorRateThreshold() * 100));
        }
        if (verifyFail > obs.getVerifyFailureThreshold()) {
            fire("verify_failure", obs, "支付验签失败告警",
                    "近周期支付回调验签失败累计 " + verifyFail + " 次，超阈值 " + obs.getVerifyFailureThreshold());
        }
        StringBuilder slow = new StringBuilder();
        for (Map<String, Object> t : timers) {
            String name = String.valueOf(t.get("name"));
            if (name.startsWith(T_LATENCY)) {
                long avg = ((Number) t.get("avgMs")).longValue();
                if (avg > obs.getSlowRequestMs()) {
                    slow.append(name).append(" 均值 ").append(avg).append("ms；");
                }
            }
        }
        if (slow.length() > 0) {
            fire("slow_request", obs, "慢请求告警", slow.toString());
        }
    }

    private void fire(String key, IdlefishProperties.Observability obs, String title, String content) {
        Instant now = Instant.now();
        Instant prev = lastAlerted.get(key);
        if (prev != null && now.minusSeconds(obs.getCooldownMinutes() * 60).isBefore(prev)) {
            return; // 冷却期内跳过，避免轰炸
        }
        lastAlerted.put(key, now);
        try {
            notificationService.notify(obs.getAlertAdminUserId(), NotificationType.SYSTEM_ALERT,
                    "alert:" + key, title, content);
        } catch (Exception e) {
            log.warn("[alert] 发送告警失败 key={}: {}", key, e.getMessage());
        }
    }

    private long sumPrefix(List<Map<String, Object>> counters, String prefix) {
        if (counters == null) {
            return 0;
        }
        long sum = 0;
        for (Map<String, Object> c : counters) {
            if (String.valueOf(c.get("name")).startsWith(prefix)) {
                sum += ((Number) c.get("value")).longValue();
            }
        }
        return sum;
    }

    private long valueOf(List<Map<String, Object>> counters, String name) {
        if (counters == null) {
            return 0;
        }
        for (Map<String, Object> c : counters) {
            if (name.equals(String.valueOf(c.get("name")))) {
                return ((Number) c.get("value")).longValue();
            }
        }
        return 0;
    }
}
