package com.idlefish.trade.common.observability.micrometer;

import com.idlefish.trade.common.observability.MetricsRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * F-12.6 可观测性增强：将零依赖 {@link MetricsRegistry} 桥接到 Micrometer {@link MeterRegistry}，
 * 经 {@code /actuator/prometheus} 暴露 Prometheus 文本指标。
 *
 * <p>设计要点：
 * <ul>
 *   <li>仅在本 profile（micrometer）激活时编译与生效：本类位于独立的源码根
 *       {@code src/main/micrometer/java}，actuator/micrometer 依赖也仅在 micrometer profile 引入，
 *       因此默认离线 {@code mvn -o} 构建完全不受影响。</li>
 *   <li>{@link #sync()} 周期性扫描 MetricsRegistry 快照，为新增指标名注册 Gauge；
 *       Gauge 以弱引用读取缓存快照的实时值，避免与零依赖注册表重复计数（仅镜像读数）。</li>
 *   <li>计数器 → {@code idlefish_counter{name="..."}}（baseUnit=count）；
 *       计时器 → {@code idlefish_timer_count{name="..."}} /
 *       {@code idlefish_timer_sum_ms{name="..."}} / {@code idlefish_timer_max_ms{name="..."}}。</li>
 * </ul>
 */
@Configuration
@Profile("micrometer")
public class MicrometerMetricsBridge {

    private final MetricsRegistry registry;
    private final MeterRegistry meterRegistry;
    private final Set<String> registered = ConcurrentHashMap.newKeySet();
    private volatile Map<String, Object> latest = Map.of();

    public MicrometerMetricsBridge(MetricsRegistry registry, MeterRegistry meterRegistry) {
        this.registry = registry;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        sync();
    }

    /**
     * 每 5s 刷新快照并发现新指标名（指标名多为运行时动态产生，如按 outcome 追加的
     * {@code *.success}/{@code *.failure}）。Gauge 自身不持有变化值，只镜像 latest 快照。
     */
    @Scheduled(fixedDelay = 5000)
    public void sync() {
        latest = registry.snapshot();
        scanCounters();
        scanTimers();
    }

    @SuppressWarnings("unchecked")
    private void scanCounters() {
        List<Map<String, Object>> counters = (List<Map<String, Object>>) latest.get("counters");
        if (counters == null) {
            return;
        }
        for (Map<String, Object> c : counters) {
            String orig = String.valueOf(c.get("name"));
            if (registered.add("counter:" + orig)) {
                final String name = orig;
                Gauge.builder("idlefish_counter", this, obj -> counterValue(name))
                        .tag("name", sanitize(orig))
                        .baseUnit("count")
                        .description("Business counter bridged from zero-dependency MetricsRegistry")
                        .register(meterRegistry);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void scanTimers() {
        List<Map<String, Object>> timers = (List<Map<String, Object>>) latest.get("timers");
        if (timers == null) {
            return;
        }
        for (Map<String, Object> t : timers) {
            String orig = String.valueOf(t.get("name"));
            if (registered.add("timer:" + orig)) {
                final String name = orig;
                Gauge.builder("idlefish_timer_count", this, obj -> timerValue(name, "count"))
                        .tag("name", sanitize(orig)).baseUnit("count").register(meterRegistry);
                Gauge.builder("idlefish_timer_sum_ms", this, obj -> timerValue(name, "sumMs"))
                        .tag("name", sanitize(orig)).baseUnit("milliseconds").register(meterRegistry);
                Gauge.builder("idlefish_timer_max_ms", this, obj -> timerValue(name, "maxMs"))
                        .tag("name", sanitize(orig)).baseUnit("milliseconds").register(meterRegistry);
            }
        }
    }

    private double counterValue(String origName) {
        Map<String, Object> snap = latest;
        List<Map<String, Object>> counters = (List<Map<String, Object>>) snap.get("counters");
        if (counters == null) {
            return 0d;
        }
        for (Map<String, Object> c : counters) {
            if (origName.equals(c.get("name"))) {
                return ((Number) c.get("value")).doubleValue();
            }
        }
        return 0d;
    }

    private double timerValue(String origName, String field) {
        Map<String, Object> snap = latest;
        List<Map<String, Object>> timers = (List<Map<String, Object>>) snap.get("timers");
        if (timers == null) {
            return 0d;
        }
        for (Map<String, Object> t : timers) {
            if (origName.equals(t.get("name"))) {
                Object v = t.get(field);
                return v == null ? 0d : ((Number) v).doubleValue();
            }
        }
        return 0d;
    }

    /**
     * Prometheus label 值允许任意字符串，但做基本净化（去除换行/引号并 trim），
     * 防止异常指标名污染标签维度。
     */
    private static String sanitize(String name) {
        if (name == null) {
            return "";
        }
        return name.replace("\n", "").replace("\r", "").replace("\"", "").trim();
    }
}
