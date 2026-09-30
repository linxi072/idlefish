package com.idlefish.trade.common.lock;

/**
 * 分布式锁抽象（强语义：要么拿到、要么明确失败）。
 * <p>
 * 与 {@code CacheService} 的 best-effort 语义刻意分离：缓存不可用不应影响主业务，
 * 而锁需要明确的成败判定，不可因缓存抖动被误判为"获取失败"。
 */
public interface DistributedLock {

    /**
     * 尝试获取锁。
     *
     * @param key     锁键
     * @param waitMs  最长等待毫秒（超时快速失败，不无限阻塞）
     * @param leaseMs 持锁租约毫秒（到期自动释放，防止进程崩溃导致死锁）
     * @return 成功返回令牌 token（释放时用于身份校验）；失败返回 {@code null}
     */
    String tryLock(String key, long waitMs, long leaseMs);

    /**
     * 释放锁：仅当 token 匹配时才删除，避免误删他人锁。
     *
     * @param key   锁键
     * @param token {@link #tryLock} 返回的令牌
     * @return 是否释放成功
     */
    boolean unlock(String key, String token);
}
