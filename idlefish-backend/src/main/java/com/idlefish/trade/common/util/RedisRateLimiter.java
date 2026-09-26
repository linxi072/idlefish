package com.idlefish.trade.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 令牌桶限流（F-15.2 分布式限流，跨实例共享配额）。
 *
 * 采用 Lua 原子脚本在 Redis 单线程内完成「读取→回填→扣减→写回」，多实例并发安全，
 * 不依赖应用层时钟同步。算法与 {@link TokenBucket} 纯函数保持一致（满桶突发 + 恒定速率回填）。
 *
 * 故障语义：Redis 不可用时 {@link #tryAcquire} 抛出异常，由调用方（{@code RateLimitInterceptor}）
 * 降级到 {@link SimpleRateLimiter} 本地兜底——遵循「缓存/限流不可用不阻断核心链路」的工程约定。
 * 若要硬性 fail-open（Redis 挂也放行），可在调用处捕获后直接返回 true。
 *
 * 注意：Lua 仅返回整数 allow(1/0)，令牌余量以字符串形式持久化在 Redis Hash 中（保精度），
 * 规避 Redis 协议对 Lua 浮点返回值的截断问题。
 */
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    /** Lua 令牌桶：KEYS[1]=限流键；ARGV=capacity,ratePerSec,nowMs,requested,ttlSec。返回 1=放行 / 0=拒绝。 */
    private static final String LUA = "local cap = tonumber(ARGV[1])\n"
            + "local rate = tonumber(ARGV[2])\n"
            + "local now = tonumber(ARGV[3])\n"
            + "local req = tonumber(ARGV[4])\n"
            + "local ttl = tonumber(ARGV[5])\n"
            + "local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')\n"
            + "local tokens = tonumber(data[1])\n"
            + "local ts = tonumber(data[2])\n"
            + "if tokens == nil then tokens = cap; ts = now end\n"
            + "local delta = (now - ts) / 1000 * rate\n"
            + "if delta < 0 then delta = 0 end\n"
            + "tokens = tokens + delta\n"
            + "if tokens > cap then tokens = cap end\n"
            + "local allowed = 0\n"
            + "if tokens >= req then tokens = tokens - req; allowed = 1 end\n"
            + "redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))\n"
            + "redis.call('PEXPIRE', KEYS[1], math.floor(ttl * 1000))\n"
            + "return allowed\n";

    private final RedisTemplate<String, Object> redisTemplate;
    private final double capacity;
    private final double ratePerSec;
    private final long ttlSeconds;
    private final RedisScript<Long> script;

    public RedisRateLimiter(RedisTemplate<String, Object> redisTemplate, long permitsPerMinute) {
        this.redisTemplate = redisTemplate;
        this.capacity = permitsPerMinute;
        this.ratePerSec = permitsPerMinute / 60.0;
        this.ttlSeconds = 120L; // 约等于 2 分钟窗口，桶空闲后自动回收
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    /**
     * 尝试获取一次配额（分布式）。
     *
     * @param key 限流维度键（如 {@code clientIp:path}）
     * @return true=放行；false=限流拒绝
     * @throws RuntimeException Redis 不可用时抛出，由调用方降级到本地限流兜底
     */
    public boolean tryAcquire(String key) {
        Long allowed = redisTemplate.execute(script,
                Collections.singletonList("rl:" + key),
                String.valueOf(capacity),
                String.valueOf(ratePerSec),
                String.valueOf(System.currentTimeMillis()),
                "1",
                String.valueOf(ttlSeconds));
        return decide(allowed);
    }

    /** 观测当前桶剩余令牌（监控用）；键不存在视为满桶。 */
    public long used(String key) {
        Object v = redisTemplate.opsForHash().get("rl:" + key, "tokens");
        if (v == null) {
            return (long) capacity;
        }
        return (long) Math.floor(Double.parseDouble(String.valueOf(v)));
    }

    /** 结果映射（与 {@link #tryAcquire} 解耦，便于离线单测无 Redis 依赖）。 */
    static boolean decide(Long allowed) {
        return allowed != null && allowed == 1L;
    }
}
