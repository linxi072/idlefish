package com.idlefish.trade.common.observability.micrometer;

import com.idlefish.trade.common.observability.DependencyProbes;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 短信网关健康指标（micrometer profile 生效）。
 * <p>短信仅展示配置态（不主动外呼探测），始终 {@code UP}；复用 {@link DependencyProbes#probeSms()}。
 */
@Component("sms")
@Profile("micrometer")
public class SmsHealthIndicator implements HealthIndicator {

    private final DependencyProbes probes;

    public SmsHealthIndicator(DependencyProbes probes) {
        this.probes = probes;
    }

    @Override
    public Health health() {
        DependencyProbes.ProbeResult r = probes.probeSms();
        return Health.up().withDetail("status", r.getStatus()).withDetail("detail", r.getDetail()).build();
    }
}
