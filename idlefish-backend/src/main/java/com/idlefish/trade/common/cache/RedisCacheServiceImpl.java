package com.idlefish.trade.common.cache;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存实现：基于 RedisTemplate<String,Object>（JSON 序列化）。
 * 系统唯一缓存实现，由 RedisCacheAutoConfig 始终装配。
 */
public class RedisCacheServiceImpl implements CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public <T> void put(String key, T value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public <T> void put(String key, T value, long ttl, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, ttl, unit);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    @Override
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void evict(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public void evictPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (!CollectionUtils.isEmpty(keys)) {
            redisTemplate.delete(keys);
        }
    }

    @Override
    public long increment(String key, long delta) {
        Long v = redisTemplate.opsForValue().increment(key, delta);
        return v == null ? delta : v;
    }

    @Override
    public void expire(String key, long ttl, TimeUnit unit) {
        redisTemplate.expire(key, ttl, unit);
    }
}
