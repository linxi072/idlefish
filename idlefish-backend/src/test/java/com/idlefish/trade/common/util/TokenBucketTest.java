package com.idlefish.trade.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 令牌桶算法纯函数单测（F-15.2 分布式限流算法内核）。
 * 离线、零依赖，验证令牌桶与固定窗口的差异（允许突发 + 恒定速率回填）。
 */
class TokenBucketTest {

    /** 桶容量 3、回填 0.05 令牌/秒（=3/min）：前 3 次放行，第 4 次拒绝。 */
    @Test
    @DisplayName("满桶允许至 capacity 次，超出即拒绝")
    void allowsUpToCapacityThenRejects() {
        TokenBucket.State s = null;
        TokenBucket.Decision d;
        d = TokenBucket.eval(s, 0L, 3, 0.05, 1);
        assertTrue(d.allowed);
        assertEquals(2.0, d.next.tokens, 1e-9);
        d = TokenBucket.eval(d.next, 0L, 3, 0.05, 1);
        assertTrue(d.allowed);
        d = TokenBucket.eval(d.next, 0L, 3, 0.05, 1);
        assertTrue(d.allowed);
        assertEquals(0.0, d.next.tokens, 1e-9);
        d = TokenBucket.eval(d.next, 0L, 3, 0.05, 1);
        assertFalse(d.allowed, "第 4 次桶空应被拒绝");
    }

    /** 20 秒后回填 1 令牌（20*0.05），空桶恢复 1 次放行。 */
    @Test
    @DisplayName("时间回填后桶恢复可用配额")
    void refillsOverTime() {
        TokenBucket.State s = new TokenBucket.State(0.0, 0L);
        TokenBucket.Decision d = TokenBucket.eval(s, 20_000L, 3, 0.05, 1);
        assertTrue(d.allowed, "20s 回填 1 令牌，应放行");
        assertEquals(0.0, d.next.tokens, 1e-9);
        TokenBucket.Decision again = TokenBucket.eval(d.next, 20_000L, 3, 0.05, 1);
        assertFalse(again.allowed, "未再回填应拒绝");
    }

    /** 时钟回拨（now < ts）时回填量不为负，不倒扣令牌（仅消费本次请求令牌）。 */
    @Test
    @DisplayName("时钟回拨不产生负回填")
    void clockSkewNoNegativeRefill() {
        TokenBucket.State s = new TokenBucket.State(1.0, 1000L);
        TokenBucket.Decision d = TokenBucket.eval(s, 500L, 3, 0.05, 1);
        assertEquals(0.0, d.next.tokens, 1e-9, "时钟回拨不增加令牌（仅消费本次请求令牌 1）");
        assertTrue(d.allowed);
    }

    /** 桶容量封顶：长时无请求后令牌封顶为 capacity，本次消费 1 后余 capacity-1。 */
    @Test
    @DisplayName("长时间空闲令牌封顶不超 capacity")
    void capsAtCapacity() {
        TokenBucket.State s = new TokenBucket.State(0.0, 0L);
        TokenBucket.Decision d = TokenBucket.eval(s, 10_000_000L, 3, 0.05, 1);
        assertEquals(2.0, d.next.tokens, 1e-9, "远超时长应封顶为 capacity(3)，消费 1 后余 2");
        assertTrue(d.allowed);
    }

    /** 零容量：任何请求均被拒绝。 */
    @Test
    @DisplayName("零容量：任何请求均被拒绝")
    void zeroCapacityRejectsAll() {
        TokenBucket.Decision d = TokenBucket.eval(null, 0L, 0, 0.0, 1);
        assertFalse(d.allowed);
        assertEquals(0.0, d.next.tokens, 1e-9);
    }

    /** 大桶允许突发：单次连续取 5 次均放行。 */
    @Test
    @DisplayName("大容量桶支持突发")
    void burstAllowed() {
        TokenBucket.State s = null;
        TokenBucket.Decision d = null;
        for (int i = 0; i < 5; i++) {
            d = TokenBucket.eval(s, 0L, 100, 1.0, 1);
            assertTrue(d.allowed, "第 " + (i + 1) + " 次应放行");
            s = d.next;
        }
        assertEquals(95.0, d.next.tokens, 1e-9);
    }
}
