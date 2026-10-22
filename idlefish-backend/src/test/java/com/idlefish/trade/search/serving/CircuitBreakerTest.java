package com.idlefish.trade.search.serving;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 熔断器单测（ITER-T04 / REQ-08）：纯函数状态迁移 + 有状态行为（阈值/窗口/半开探测/复位）。
 */
class CircuitBreakerTest {

    @Test
    @DisplayName("纯函数：CLOSED 失败累计达阈值才 OPEN，未达保持")
    void pureClosedTransitions() {
        assertEquals(CircuitBreaker.State.CLOSED,
                CircuitBreaker.next(CircuitBreaker.State.CLOSED, false, 4, 5));
        assertEquals(CircuitBreaker.State.OPEN,
                CircuitBreaker.next(CircuitBreaker.State.CLOSED, false, 5, 5));
        assertEquals(CircuitBreaker.State.CLOSED,
                CircuitBreaker.next(CircuitBreaker.State.CLOSED, true, 0, 5));
    }

    @Test
    @DisplayName("纯函数：HALF_OPEN 探测成功恢复、失败再次熔断")
    void pureHalfOpenTransitions() {
        assertEquals(CircuitBreaker.State.CLOSED,
                CircuitBreaker.next(CircuitBreaker.State.HALF_OPEN, true, 0, 5));
        assertEquals(CircuitBreaker.State.OPEN,
                CircuitBreaker.next(CircuitBreaker.State.HALF_OPEN, false, 6, 5));
    }

    @Test
    @DisplayName("有状态：连续失败达阈值即熔断，窗口内拒绝请求")
    void opensAfterThreshold() {
        CircuitBreaker b = new CircuitBreaker(3, 10_000L);
        assertTrue(b.allowRequest());
        b.recordFailure();
        b.recordFailure();
        assertEquals(CircuitBreaker.State.CLOSED, b.state());
        assertTrue(b.allowRequest());
        b.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, b.state());
        assertFalse(b.allowRequest(), "OPEN 窗口内必须拒绝请求");
        assertEquals(3, b.failures());
    }

    @Test
    @DisplayName("有状态：窗口到期转半开放行探测，成功恢复、失败再熔断")
    void halfOpenProbe() throws InterruptedException {
        CircuitBreaker b = new CircuitBreaker(2, 150L);
        b.recordFailure();
        b.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, b.state());
        assertFalse(b.allowRequest());

        Thread.sleep(200L); // 越过开放窗口
        assertTrue(b.allowRequest(), "窗口到期应转半开放行探测");
        assertEquals(CircuitBreaker.State.HALF_OPEN, b.state());

        b.recordSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, b.state(), "探测成功应恢复闭合");
        assertEquals(0, b.failures());

        // 再次熔断 → 半开探测失败 → 立即重新打开
        b.recordFailure();
        b.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, b.state());
        Thread.sleep(200L);
        assertTrue(b.allowRequest());
        b.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, b.state(), "半开探测失败应再次熔断");
    }

    @Test
    @DisplayName("有状态：成功后清零失败计数；reset 立即闭合")
    void resetAndRecovery() {
        CircuitBreaker b = new CircuitBreaker(2, 10_000L);
        b.recordFailure();
        b.recordSuccess();
        assertEquals(0, b.failures());
        assertEquals(CircuitBreaker.State.CLOSED, b.state());

        b.recordFailure();
        b.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, b.state());
        b.reset();
        assertEquals(CircuitBreaker.State.CLOSED, b.state());
        assertTrue(b.allowRequest());
    }
}
