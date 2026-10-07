package com.idlefish.trade.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 零依赖指标注册表（不引入 Micrometer/Actuator，保证离线可编译）。
 * <p>
 * 提供两类指标：
 * <ul>
 *   <li>计数器 counter：业务次数（下单、支付回调、分账成功/失败等）</li>
 *   <li>计时器 timer：耗时统计（次数 / 总耗时 / 最大耗时 / 均值 / 分位数）</li>
 * </ul>
 * 指标名采用扁平命名（如 {@code pay.notify.v3.success}），避免标签维度带来的内存膨胀。
 *
 * <p>计时方法 {@link #timed(String, java.util.function.Supplier)} / {@link #timedRun(String, Runnable)}
 * 在执行后统一输出结构化 JSON span 日志（{@code traceId} / {@code action} / {@code result} / {@code costMs}），
 * 满足 REQ-04，便于日志平台按字段检索与告警。</p>
 */
@Component
public class MetricsRegistry {

    private static final Logger log = LoggerFactory.getLogger(MetricsRegistry.class);

    /** span 日志常量：有界蓄水池容量（最近 N 个样本用于分位数估算）。 */
    private static final int RESERVOIR_SIZE = 1024;

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
     * 执行结束后统一输出结构化 JSON span 日志（REQ-04）。
     */
    public <T> T timed(String name, java.util.function.Supplier<T> supplier) {
        long start = System.nanoTime();
        boolean success = false;
        try {
            T result = supplier.get();
            increment(name + ".success");
            success = true;
            return result;
        } catch (RuntimeException e) {
            increment(name + ".failure");
            throw e;
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000L;
            record(name + ".latency", costMs);
            logSpan(name, success, costMs);
        }
    }

    /**
     * 输出结构化 JSON span 日志。字段：
     * {@code traceId}（取自 {@link TraceContext#current()}，缺失则为空串）、
     * {@code action}、{@code result}（success/failure）、{@code costMs}、{@code ts}（毫秒时间戳）。
     * 日志级别 INFO，可通过调整 logger 级别抑制；JSON 行可被日志平台按字段解析。
     */
    private void logSpan(String action, boolean success, long costMs) {
        String traceId = TraceContext.current();
        String span = "{\"traceId\":\"" + (traceId == null ? "" : traceId)
                + "\",\"action\":\"" + action
                + "\",\"result\":\"" + (success ? "success" : "failure")
                + "\",\"costMs\":" + costMs
                + ",\"ts\":" + System.currentTimeMillis() + "}";
        log.info(span);
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
            m.put("p50Ms", v.p50());
            m.put("p95Ms", v.p95());
            m.put("p99Ms", v.p99());
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

    /**
     * 计时器：次数 / 总耗时 / 最大耗时 / 分位数。
     * <p>分位数基于「最近 {@value #RESERVOIR_SIZE} 个样本」的有界蓄水池（环形覆盖）近似估算，
     * 用于尾部延迟（p99）SLO 告警。样本量不足时返回 0；并发写入为 best-effort（监控用途，允许近似）。</p>
     */
    public static class Timer {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong sumMs = new AtomicLong();
        private final AtomicLong maxMs = new AtomicLong();
        private final long[] samples = new long[RESERVOIR_SIZE];
        private final AtomicInteger sampleFilled = new AtomicInteger(0);
        private final AtomicInteger sampleCursor = new AtomicInteger(0);

        void record(long millis) {
            count.incrementAndGet();
            sumMs.addAndGet(millis);
            maxMs.updateAndGet(prev -> Math.max(prev, millis));
            int idx = sampleCursor.getAndUpdate(c -> (c + 1) % RESERVOIR_SIZE);
            samples[idx] = millis;
            int filled = sampleFilled.get();
            if (filled < RESERVOIR_SIZE) {
                sampleFilled.compareAndSet(filled, filled + 1);
            }
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

        /** 最近 N 个样本的升序副本，用于分位数计算。 */
        private long[] sortedSamples() {
            int n = sampleFilled.get();
            long[] copy = new long[n];
            for (int i = 0; i < n; i++) {
                copy[i] = samples[i];
            }
            Arrays.sort(copy);
            return copy;
        }

        /** 最近 N 个样本的第 p 分位数（nearest-rank），样本不足返回 0。 */
        public long percentile(double p) {
            if (p <= 0.0) {
                p = 0.0;
            }
            if (p > 1.0) {
                p = 1.0;
            }
            int n = sampleFilled.get();
            if (n == 0) {
                return 0L;
            }
            long[] sorted = sortedSamples();
            int rank = (int) Math.ceil(p * n) - 1;
            if (rank < 0) {
                rank = 0;
            }
            if (rank >= n) {
                rank = n - 1;
            }
            return sorted[rank];
        }

        public long p50() {
            return percentile(0.50);
        }

        public long p95() {
            return percentile(0.95);
        }

        public long p99() {
            return percentile(0.99);
        }
    }
}
