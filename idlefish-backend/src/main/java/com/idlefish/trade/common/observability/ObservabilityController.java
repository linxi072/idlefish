package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可观测性端点（零依赖，替代 Actuator）。
 * <p>
 * 路径置于 {@code /actuator/**}，已在 WebConfig 中放行鉴权，便于 Prometheus/探活直接抓取。
 * 生产环境建议由网关收敛为内网可访问，避免对外暴露指标细节。
 * <p>
 * Profile 约束：本端点仅在非 micrometer 模式下生效（{@code @Profile("!micrometer")}）。
 * 当显式激活 micrometer profile（F-12.6，T01 起默认激活）时，由 Spring Boot Actuator 接管
 * {@code /actuator/health}、{@code /actuator/metrics} 与 {@code /actuator/prometheus}，
 * 避免同路径 Ambiguous mapping；业务指标经 MicrometerMetricsBridge 桥接为 Prometheus 格式暴露。
 * <p>
 * {@code /actuator/health} 在进程级探活基础上扩展依赖探活（F-12.5 / T01）：
 * <ul>
 *   <li>MySQL 为关键依赖，探活失败直接判定整体 {@code DOWN}；</li>
 *   <li>Redis/ES/RocketMQ/OSS 为可选外部依赖，依据配置 + TCP 可达性探测，
 *       不可达时标记 {@code DEGRADED}（降级告警），不误杀进程；</li>
 *   <li>依赖探活逻辑统一收敛到 {@link DependencyProbes}（零 Actuator 依赖），
 *       与 micrometer profile 下的 6 个 HealthIndicator 复用同一实现，避免双份维护。</li>
 * </ul>
 */
@RestController
@RequestMapping("/actuator")
@Profile("!micrometer")
public class ObservabilityController {

    private final MetricsRegistry metrics;
    private final DependencyProbes probes;

    public ObservabilityController(MetricsRegistry metrics, DependencyProbes probes) {
        this.metrics = metrics;
        this.probes = probes;
    }

    /** 健康检查：进程 + 依赖。 */
    @GetMapping("/health")
    public Map<String, Object> health(HttpServletResponse response) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> deps = new LinkedHashMap<>();

        Map<String, Object> mysql = probes.probeMysql().toMap();
        deps.put("mysql", mysql);
        deps.put("redis", probes.probeRedis().toMap());
        deps.put("elasticsearch", probes.probeElasticsearch().toMap());
        deps.put("rocketmq", probes.probeRocketMq().toMap());
        deps.put("oss", probes.probeOss().toMap());
        deps.put("sms", probes.probeSms().toMap());

        boolean mysqlDown = "DOWN".equals(mysql.get("status"));
        boolean anyDegraded = deps.values().stream()
                .map(d -> (Map<?, ?>) d)
                .anyMatch(d -> "DEGRADED".equals(d.get("status")));
        String overall = mysqlDown ? "DOWN" : (anyDegraded ? "DEGRADED" : "UP");

        // T07 容器化探针语义：核心依赖（MySQL）不可用时整体不可用，返回 503 触发 k8s 摘除流量；
        // 次要依赖降级（如 ES 不可达，DEGRADED）仍返回 200，核心交易链路不受影响、不误摘流量。
        if (mysqlDown) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        body.put("status", overall);
        body.put("timestamp", Instant.now().toString());
        Runtime rt = Runtime.getRuntime();
        body.put("jvmFreeMemoryMB", rt.freeMemory() / (1024 * 1024));
        body.put("jvmTotalMemoryMB", rt.totalMemory() / (1024 * 1024));
        body.put("dependencies", deps);
        return body;
    }

    /** 指标快照：计数器与计时器。 */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return metrics.snapshot();
    }
}
