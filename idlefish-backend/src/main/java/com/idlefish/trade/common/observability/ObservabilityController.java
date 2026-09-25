package com.idlefish.trade.common.observability;

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
 */
@RestController
@RequestMapping("/actuator")
public class ObservabilityController {

    private final MetricsRegistry metrics;

    public ObservabilityController(MetricsRegistry metrics) {
        this.metrics = metrics;
    }

    /** 健康检查（进程级）。 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        Runtime rt = Runtime.getRuntime();
        body.put("jvmFreeMemoryMB", rt.freeMemory() / (1024 * 1024));
        body.put("jvmTotalMemoryMB", rt.totalMemory() / (1024 * 1024));
        return body;
    }

    /** 指标快照：计数器与计时器。 */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return metrics.snapshot();
    }
}
