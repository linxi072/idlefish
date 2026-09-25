package com.idlefish.trade.common.observability;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 零依赖指标注册表（不引入 Micrometer/Actuator，保证离线可编译）。
 * <p>
 * 提供两类指标：
 * <ul>
 *   <li>计数器 counter：业务次数（下单、支付回调、分账成功/失败等）</li>
 *   <li>计时器 timer：耗时统计（次数 / 总耗时 / 最大耗时 / 均值）</li>
 * </ul>
 * 指标名采用扁平命名（如 {@code pay.notify.v3.success}），避免标签维度带来的内存膨胀。
 */
@Component
public class MetricsRegistry {

    private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();

    /** 计数器 +1。 */
    public void increment(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
    }

    /** 读取计数器当前值。 */
    public long counter(String name) {
        AtomicLong c = counters.get(name);
        return c == null ? 0L : c.get();
    }

    /** 记录一次耗时（毫秒）。 */
    public void record(String name, long millis) {
        timers.computeIfAbsent(name, k -> new Timer()).record(millis);
    }

    /**
     * 计时并自动记录：执行 {@code supplier}，按 outcome 追加计数与耗时。
     * 异常时记录 failure 并向外抛出，绝不影响主流程语义。
     */
    public <T> T timed(String name, java.util.function.Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            T result = supplier.get();
            increment(name + ".success");
            return result;
        } catch (RuntimeException e) {
            increment(name + ".failure");
            throw e;
        } finally {
            record(name + ".latency", (System.nanoTime() - start) / 1_000_000L);
        }
    }

    /** 计时并记录（无返回值场景）。 */
    public void timedRun(String name, Runnable runnable) {
        timed(name, () -> {
            runnable.run();
            return null;
        });
    }

    /** 导出全部指标快照，供 /actuator/metrics 暴露。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> counterList = new ArrayList<>();
        counters.forEach((k, v) -> counterList.add(mapOf("name", k, "value", v.get())));
        out.put("counters", counterList);

        List<Map<String, Object>> timerList = new ArrayList<>();
        timers.forEach((k, v) -> {
            Map<String, Object> m = mapOf("name", k, "count", v.count(),
                    "sumMs", v.sumMs(), "maxMs", v.maxMs());
            long count = v.count();
            m.put("avgMs", count == 0 ? 0L : Math.round((double) v.sumMs() / count));
            timerList.add(m);
        });
        out.put("timers", timerList);
        return out;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** 计时器：次数 / 总耗时 / 最大耗时。 */
    public static class Timer {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong sumMs = new AtomicLong();
        private final AtomicLong maxMs = new AtomicLong();

        void record(long millis) {
            count.incrementAndGet();
            sumMs.addAndGet(millis);
            maxMs.updateAndGet(prev -> Math.max(prev, millis));
        }

        public long count() {
            return count.get();
        }

        public long sumMs() {
            return sumMs.get();
        }

        public long maxMs() {
            return maxMs.get();
        }
    }
}
