package com.idlefish.trade.common.observability.micrometer;

import com.idlefish.trade.common.observability.DependencyProbes;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 健康指标（micrometer profile 生效）。
 * <p>延时队列为可选组件（未配置时由 DB 兜底），降级不致命：不可达映射为 {@code UP + detail} 保活 liveness。
 * 复用 {@link DependencyProbes#probeRocketMq()}。
 */
@Component("rocketmq")
@Profile("micrometer")
public class RocketMqHealthIndicator implements HealthIndicator {

    private final DependencyProbes probes;

    public RocketMqHealthIndicator(DependencyProbes probes) {
        this.probes = probes;
    }

    @Override
    public Health health() {
        DependencyProbes.ProbeResult r = probes.probeRocketMq();
        return Health.up().withDetail("status", r.getStatus()).withDetail("detail", r.getDetail()).build();
    }
}
