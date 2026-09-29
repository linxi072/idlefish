package com.idlefish.trade.common.cache;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * 按前缀批量失效缓存。
     * 使用 SCAN 游标替代 {@code keys()}：避免在生产 Redis 上执行 O(N) 阻塞命令，
     * 并通过分批 del 控制客户端内存占用。每次 SCAN 迭代最多拉取 {@code batchSize} 个键。
     */
    @Override
    public void evictPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        final String pattern = prefix + "*";
        final int batchSize = 200;
        redisTemplate.execute(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(batchSize).build();
                Cursor<byte[]> cursor = connection.scan(options);
                try {
                    List<byte[]> batch = new ArrayList<>(batchSize);
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());
                        if (batch.size() >= batchSize) {
                            connection.del(batch.toArray(new byte[0][]));
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) {
                        connection.del(batch.toArray(new byte[0][]));
                    }
                } finally {
                    try {
                        cursor.close();
                    } catch (Exception ignored) {
                        // 关闭游标失败不影响已执行的删除，仅记录忽略
                    }
                }
                return null;
            }
        });
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
