package com.idlefish.trade.common.observability.micrometer;

import com.idlefish.trade.common.observability.DependencyProbes;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * MySQL 关键依赖健康指标（micrometer profile 生效）。
 * <p>关键依赖不可达 → 整体 {@code DOWN}（K8s 将重启 Pod）；
 * 统一复用 {@link DependencyProbes#probeMysql()} 的零依赖 TCP/SQL 探活，与
 * 非 micrometer 模式下的 {@code /actuator/health} 同源，避免双份实现漂移。
 */
@Component("mysql")
@Profile("micrometer")
public class MysqlHealthIndicator implements HealthIndicator {

    private final DependencyProbes probes;

    public MysqlHealthIndicator(DependencyProbes probes) {
        this.probes = probes;
    }

    @Override
    public Health health() {
        DependencyProbes.ProbeResult r = probes.probeMysql();
        if (r.isDown()) {
            return Health.down().withDetail("status", r.getStatus()).withDetail("detail", r.getDetail()).build();
        }
        return Health.up().withDetail("status", r.getStatus()).withDetail("detail", r.getDetail()).build();
    }
}
