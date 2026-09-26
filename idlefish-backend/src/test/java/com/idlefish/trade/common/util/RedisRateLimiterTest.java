package com.idlefish.trade.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 令牌桶限流单测（F-15.2）：聚焦结果映射 {@link RedisRateLimiter#decide} 的离线验证。
 *
 * 说明：Lua 脚本执行需真实 Redis 实例（沙箱无 Redis，由 CI/本机集成测试覆盖）；此处仅验证
 * Redis 返回码 → 放行/拒绝 的映射契约，不依赖 Redis 连接，保证离线可跑。
 */
class RedisRateLimiterTest {

    @Test
    @DisplayName("Redis 返回 1 表示放行")
    void allowWhenOne() {
        assertTrue(RedisRateLimiter.decide(1L));
    }

    @Test
    @DisplayName("Redis 返回 0 表示拒绝")
    void rejectWhenZero() {
        assertFalse(RedisRateLimiter.decide(0L));
    }

    @Test
    @DisplayName("Redis 异常/无返回（null）视为拒绝（由拦截器降级本地兜底）")
    void rejectWhenNull() {
        assertFalse(RedisRateLimiter.decide(null));
    }
}
