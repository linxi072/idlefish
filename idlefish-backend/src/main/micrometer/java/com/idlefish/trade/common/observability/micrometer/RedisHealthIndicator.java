package com.idlefish.trade.common.observability.micrometer;

import com.idlefish.trade.common.observability.DependencyProbes;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Redis 健康指标（micrometer profile 生效）。
 * <p>Redis 为系统唯一缓存组件，但降级不致命：不可达（{@code DEGRADED}）时映射为
 * {@code UP + detail} 以保活 K8s liveness，仅透出 detail 供告警/看板观测，不触发 Pod 重启。
 * 复用 {@link DependencyProbes#probeRedis()}。
 */
@Component("redis")
@Profile("micrometer")
public class RedisHealthIndicator implements HealthIndicator {

    private final DependencyProbes probes;

    public RedisHealthIndicator(DependencyProbes probes) {
        this.probes = probes;
    }

    @Override
    public Health health() {
        DependencyProbes.ProbeResult r = probes.probeRedis();
        return Health.up().withDetail("status", r.getStatus()).withDetail("detail", r.getDetail()).build();
    }
}
