package com.idlefish.trade.common.observability.micrometer;

import com.idlefish.trade.common.observability.DependencyProbes;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * OSS 健康指标（micrometer profile 生效）。
 * <p>对象存储为可选外部依赖，降级不致命：不可达映射为 {@code UP + detail} 保活 liveness。
 * 复用 {@link DependencyProbes#probeOss()}。
 */
@Component("oss")
@Profile("micrometer")
public class OssHealthIndicator implements HealthIndicator {

    private final DependencyProbes probes;

    public OssHealthIndicator(DependencyProbes probes) {
        this.probes = probes;
    }

    @Override
    public Health health() {
        DependencyProbes.ProbeResult r = probes.probeOss();
        return Health.up().withDetail("status", r.getStatus()).withDetail("detail", r.getDetail()).build();
    }
}
