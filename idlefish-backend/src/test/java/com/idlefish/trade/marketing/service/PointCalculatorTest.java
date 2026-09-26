package com.idlefish.trade.marketing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 积分纯函数单测（F-13.1）：无数据库 / 无 Spring，验证计算确定性与边界。
 */
class PointCalculatorTest {

    private static final int PPU = 100;       // 100 积分抵 1 元（100 分）
    private static final double RATIO = 0.5;  // 最多抵扣应付 50%

    @Test
    @DisplayName("积分足额：请求 1000 积分抵 100 元订单的 10 元")
    void redeemFull() {
        PointCalculator.PointRedeemResult r = PointCalculator.calcRedeem(1000, 1000, 10000, PPU, RATIO);
        assertEquals(1000, r.usedPoints);
        assertEquals(1000, r.discountFen); // 10 元
    }

    @Test
    @DisplayName("按比例封顶：请求远超上限，实际使用受 50% 应付限制")
    void redeemCappedByRatio() {
        // 应付 100 元 → 最多抵 50 元 → 最多用 5000 积分
        PointCalculator.PointRedeemResult r = PointCalculator.calcRedeem(100000, 100000, 10000, PPU, RATIO);
        assertEquals(5000, r.usedPoints);
        assertEquals(5000, r.discountFen);
    }

    @Test
    @DisplayName("可用积分不足：以可用值为上限")
    void redeemLimitedByAvailable() {
        PointCalculator.PointRedeemResult r = PointCalculator.calcRedeem(1000, 500, 10000, PPU, RATIO);
        assertEquals(500, r.usedPoints);
        assertEquals(500, r.discountFen);
    }

    @Test
    @DisplayName("抵扣比例 0 时不可抵现")
    void redeemZeroRatio() {
        PointCalculator.PointRedeemResult r = PointCalculator.calcRedeem(1000, 1000, 10000, PPU, 0);
        assertEquals(0, r.usedPoints);
        assertEquals(0, r.discountFen);
    }

    @Test
    @DisplayName("非法入参（请求/可用/应付为 0 或兑换比例非正）返回 0")
    void redeemInvalidInputs() {
        assertEquals(0, PointCalculator.calcRedeem(0, 100, 10000, PPU, RATIO).discountFen);
        assertEquals(0, PointCalculator.calcRedeem(100, 0, 10000, PPU, RATIO).discountFen);
        assertEquals(0, PointCalculator.calcRedeem(100, 100, 0, PPU, RATIO).discountFen);
        assertEquals(0, PointCalculator.calcRedeem(100, 100, 10000, 0, RATIO).discountFen);
    }

    @Test
    @DisplayName("签到：首日基础分，连续加成封顶")
    void signinPoints() {
        assertEquals(5, PointCalculator.calcSigninPoints(5, 1, 10));
        assertEquals(7, PointCalculator.calcSigninPoints(5, 3, 10));
        assertEquals(15, PointCalculator.calcSigninPoints(5, 20, 10)); // 封顶 +10
        assertEquals(0, PointCalculator.calcSigninPoints(5, 0, 10));
    }

    @Test
    @DisplayName("交易得积分：按元换算并封顶")
    void tradePoints() {
        assertEquals(100, PointCalculator.calcTradePoints(10000, 1, 1000));   // 100 元 → 100 分
        assertEquals(1000, PointCalculator.calcTradePoints(100000, 1, 1000));  // 封顶 1000
        assertEquals(0, PointCalculator.calcTradePoints(0, 1, 1000));
        assertEquals(0, PointCalculator.calcTradePoints(10000, 0, 1000));
    }

    @Test
    @DisplayName("评价得积分：固定值非负")
    void reviewPoints() {
        assertEquals(10, PointCalculator.calcReviewPoints(10));
        assertEquals(0, PointCalculator.calcReviewPoints(-5));
    }
}
