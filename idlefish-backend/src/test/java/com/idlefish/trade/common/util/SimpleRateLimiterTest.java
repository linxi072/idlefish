package com.idlefish.trade.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 限流骨架单测（F-04 安全加固·限流）。
 * 纯逻辑、零依赖，验证固定窗口计数的配额与 key 隔离。
 */
class SimpleRateLimiterTest {

    @Test
    @DisplayName("窗口内允许至 permits 次，超出即拒绝")
    void allowsUpToPermitsThenRejects() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(3, 60);
        assertTrue(limiter.tryAcquire("k"));
        assertTrue(limiter.tryAcquire("k"));
        assertTrue(limiter.tryAcquire("k"));
        assertFalse(limiter.tryAcquire("k"), "第 4 次应被限流拒绝");
        assertEquals(3L, limiter.used("k"));
    }

    @Test
    @DisplayName("不同 key 的配额相互独立")
    void keysAreIndependent() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(1, 60);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"), "a 已达上限");
        assertTrue(limiter.tryAcquire("b"), "b 为不同 key，不受影响");
    }

    @Test
    @DisplayName("零配额：任何请求均被拒绝")
    void zeroPermitsRejectsAll() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(0, 60);
        assertFalse(limiter.tryAcquire("any"));
        assertFalse(limiter.tryAcquire("any"));
    }
}
