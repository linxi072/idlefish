package com.idlefish.trade.common.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地内存缓存实现（默认）：基于 ConcurrentHashMap + 过期时间戳。
 * 适用于单体/开发环境；分布式部署请切换 Redis 实现。
 * 通过 idlefish.cache.type=local（默认缺省即 local）激活。
 */
@Component
@ConditionalOnProperty(name = "idlefish.cache.type", havingValue = "local", matchIfMissing = true)
public class LocalCacheServiceImpl implements CacheService {

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> expireAt = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "local-cache-cleaner");
                t.setDaemon(true);
                return t;
            });

    public LocalCacheServiceImpl() {
        cleaner.scheduleAtFixedRate(this::cleanup, 30, 30, TimeUnit.SECONDS);
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = expireAt.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() != null && e.getValue() <= now) {
                it.remove();
                store.remove(e.getKey());
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void put(String key, T value) {
        store.put(key, value);
        expireAt.remove(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void put(String key, T value, long ttl, TimeUnit unit) {
        store.put(key, value);
        expireAt.put(key, System.currentTimeMillis() + unit.toMillis(ttl));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        Long exp = expireAt.get(key);
        if (exp != null && exp <= System.currentTimeMillis()) {
            expireAt.remove(key);
            store.remove(key);
            return null;
        }
        return (T) store.get(key);
    }

    @Override
    public boolean hasKey(String key) {
        return get(key) != null;
    }

    @Override
    public void evict(String key) {
        store.remove(key);
        expireAt.remove(key);
    }

    @Override
    public void evictPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        store.keySet().removeIf(k -> k.startsWith(prefix));
        expireAt.keySet().removeIf(k -> k.startsWith(prefix));
    }

    @Override
    public long increment(String key, long delta) {
        Object v = store.get(key);
        long cur = (v instanceof Number) ? ((Number) v).longValue() : 0L;
        long next = cur + delta;
        store.put(key, new AtomicLong(next));
        return next;
    }

    @Override
    public void expire(String key, long ttl, TimeUnit unit) {
        if (store.containsKey(key)) {
            expireAt.put(key, System.currentTimeMillis() + unit.toMillis(ttl));
        }
    }
}
