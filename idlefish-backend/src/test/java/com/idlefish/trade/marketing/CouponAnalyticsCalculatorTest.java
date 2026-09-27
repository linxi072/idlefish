package com.idlefish.trade.marketing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CouponAnalyticsCalculator 纯函数单测（F-13.5，离线可跑）。
 */
class CouponAnalyticsCalculatorTest {

    @Test
    void redemptionRate_zeroIssued_returnsZero() {
        assertEquals(0.0, CouponAnalyticsCalculator.redemptionRate(0, 0));
        assertEquals(0.0, CouponAnalyticsCalculator.redemptionRate(0, 5));
    }

    @Test
    void redemptionRate_normal() {
        assertEquals(0.4, CouponAnalyticsCalculator.redemptionRate(100, 40));
        assertEquals(1.0, CouponAnalyticsCalculator.redemptionRate(10, 10));
        assertEquals(0.0, CouponAnalyticsCalculator.redemptionRate(100, 0));
    }

    @Test
    void roi_zeroCost_returnsZero() {
        assertEquals(0.0, CouponAnalyticsCalculator.roi(1000, 0));
    }

    @Test
    void roi_positive() {
        // (1000 - 200) / 200 = 4.0
        assertEquals(4.0, CouponAnalyticsCalculator.roi(1000, 200));
    }

    @Test
    void roi_breakEven_and_negative() {
        assertEquals(0.0, CouponAnalyticsCalculator.roi(200, 200));
        // (100 - 200) / 200 = -0.5
        assertEquals(-0.5, CouponAnalyticsCalculator.roi(100, 200));
    }
}
