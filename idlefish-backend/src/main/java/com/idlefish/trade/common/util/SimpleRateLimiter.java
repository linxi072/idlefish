package com.idlefish.trade.common.util;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存限流骨架（F-04 安全加固·限流）。
 *
 * 采用「固定窗口计数」：每个 key 在长度为 {@code windowSeconds} 的窗口内允许 {@code permitsPerWindow}
 * 次请求，超过即拒绝。线程安全、零外部依赖，便于离线单测与本地演示。
 *
 * 生产环境应替换为 Redis 令牌桶（分布式共享配额）或网关层（如 Nginx/APISIX/Sentinel）限流；
 * 此处提供可运行骨架与配套单测，作为接入真实限流的契约与本地兜底。
 */
public class SimpleRateLimiter {

    private final long permitsPerWindow;
    private final long windowSeconds;
    private final Map<String, Window> buckets = new ConcurrentHashMap<>();

    public SimpleRateLimiter(long permitsPerWindow, long windowSeconds) {
        this.permitsPerWindow = permitsPerWindow;
        this.windowSeconds = windowSeconds;
    }

    /**
     * 尝试获取一次配额。
     *
     * @return true=放行；false=限流拒绝（超出窗口配额）
     */
    public boolean tryAcquire(String key) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;
        Window w = buckets.computeIfAbsent(key, k -> new Window(windowStart, new AtomicLong(0)));
        // 跨窗口：重置计数（double-check 保证并发安全）
        if (w.windowStart != windowStart) {
            synchronized (w) {
                if (w.windowStart != windowStart) {
                    w.windowStart = windowStart;
                    w.count.set(0);
                }
            }
        }
        long used = w.count.incrementAndGet();
        return used <= permitsPerWindow;
    }

    /** 观测当前窗口已用配额（测试/监控用）。 */
    public long used(String key) {
        Window w = buckets.get(key);
        return w == null ? 0L : w.count.get();
    }

    private static final class Window {
        volatile long windowStart;
        final AtomicLong count;

        Window(long windowStart, AtomicLong count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
