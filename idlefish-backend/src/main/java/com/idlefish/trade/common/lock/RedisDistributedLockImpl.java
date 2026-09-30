package com.idlefish.trade.common.lock;

import com.idlefish.trade.common.observability.MetricsRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Redis 分布式锁实现（SET NX PX 获取 + Lua 校验令牌释放）。
 * <p>
 * 关键正确性：释放必须校验 token，否则会出现"A 业务超时、锁自动过期 → B 拿到锁 → A 释放掉 B 的锁"的误删问题。
 * <p>
 * 可用性：Redis 不可用时 {@link #tryLock} 返回 {@code null}（fail-open 有界），
 * 由调用方降级到数据库条件更新——正确性始终由 DB 的 {@code WHERE stock >= qty} 保证，绝不超卖。
 */
@Slf4j
@Component
public class RedisDistributedLockImpl implements DistributedLock {

    /** 释放锁 Lua：仅当值等于 token 时才删除（get+del 必须原子，否则仍有误删窗口）。 */
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final long RETRY_INTERVAL_MS = 20L;

    private final RedisTemplate<String, Object> redisTemplate;
    private final MetricsRegistry metrics;

    public RedisDistributedLockImpl(RedisTemplate<String, Object> redisTemplate, MetricsRegistry metrics) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
    }

    @Override
    public String tryLock(String key, long waitMs, long leaseMs) {
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + Math.max(0L, waitMs);
        try {
            do {
                Boolean ok = redisTemplate.opsForValue()
                        .setIfAbsent(key, token, Duration.ofMillis(Math.max(1L, leaseMs)));
                if (Boolean.TRUE.equals(ok)) {
                    metrics.increment("lock.acquire.success");
                    return token;
                }
                sleepQuietly(RETRY_INTERVAL_MS);
            } while (System.currentTimeMillis() < deadline);
        } catch (DataAccessException e) {
            // Redis 不可用：降级（返回 null），绝不阻断主流程
            log.warn("[lock] redis unavailable, degrade to db. key={}", key, e);
            metrics.increment("lock.degrade");
            return null;
        }
        metrics.increment("lock.acquire.timeout");
        return null;
    }

    @Override
    public boolean unlock(String key, String token) {
        if (key == null || token == null) {
            return false;
        }
        try {
            Long r = redisTemplate.execute((RedisCallback<Long>) connection -> connection.eval(
                    UNLOCK_LUA.getBytes(StandardCharsets.UTF_8),
                    ReturnType.INTEGER,
                    1,
                    key.getBytes(StandardCharsets.UTF_8),
                    token.getBytes(StandardCharsets.UTF_8)));
            return r != null && r > 0L;
        } catch (DataAccessException e) {
            // 释放失败不阻断业务：租约到期会自动释放，仅记录
            log.warn("[lock] unlock failed, key={}", key, e);
            return false;
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
