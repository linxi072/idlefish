package com.idlefish.trade.common.cache;

import java.util.concurrent.TimeUnit;

/**
 * 统一缓存抽象（F-05/基础设施）：屏蔽本地与 Redis 实现差异。
 * 默认使用本地内存实现（LocalCacheServiceImpl），生产通过 idlefish.cache.type=redis 切换为 Redis 实现。
 * 所有读写均为 best-effort 语义：缓存不可用不应影响主业务流程。
 */
public interface CacheService {

    /** 写入缓存（无过期）。 */
    <T> void put(String key, T value);

    /** 写入缓存并设置 TTL。 */
    <T> void put(String key, T value, long ttl, TimeUnit unit);

    /** 读取缓存，不存在或已过期返回 null。 */
    <T> T get(String key);

    /** 是否存在（未过期）。 */
    boolean hasKey(String key);

    /** 删除缓存。 */
    void evict(String key);

    /** 删除某前缀下的所有缓存（用于批量失效，如字典类型变更）。 */
    void evictPrefix(String prefix);

    /** 原子自增（不存在则初始化为 0 后 +delta），返回自增后的值。 */
    long increment(String key, long delta);

    /** 设置/刷新过期时间。 */
    void expire(String key, long ttl, TimeUnit unit);
}
