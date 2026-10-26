package com.idlefish.trade.marketing.activity;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 砍价金额计算纯函数单测（ITER-F08 / REQ-08、REQ-12）。
 * 覆盖末刀触底、均分夹取、随机受控、已触底、配置缺失等分支。
 */
class BargainCalculatorTest {

    private BargainConfig cfg(int target, long min, long max, boolean random) {
        BargainConfig c = new BargainConfig();
        c.setTargetAssistCount(target);
        c.setMinCutFen(min);
        c.setMaxCutFen(max);
        c.setPerAssistRandom(random);
        return c;
    }

    // ===== isFloorReached =====

    @Test
    void isFloorReached_belowOrEqual_true() {
        assertTrue(BargainCalculator.isFloorReached(500, 500));
        assertTrue(BargainCalculator.isFloorReached(400, 500));
    }

    @Test
    void isFloorReached_above_false() {
        assertFalse(BargainCalculator.isFloorReached(600, 500));
    }

    // ===== isTargetReached =====

    @Test
    void isTargetReached_nullCfg_false() {
        assertFalse(BargainCalculator.isTargetReached(3, null));
    }

    @Test
    void isTargetReached_reached_true() {
        assertTrue(BargainCalculator.isTargetReached(5, cfg(5, 1, 1, false)));
    }

    @Test
    void isTargetReached_notReached_false() {
        assertFalse(BargainCalculator.isTargetReached(2, cfg(5, 1, 1, false)));
    }

    // ===== calcFinalPrice =====

    @Test
    void calcFinalPrice_normal() {
        assertEquals(700, BargainCalculator.calcFinalPrice(1000, 300));
    }

    @Test
    void calcFinalPrice_underflow_zero() {
        assertEquals(0, BargainCalculator.calcFinalPrice(200, 500));
    }

    // ===== calcNextCut =====

    @Test
    void calcNextCut_nullCfg_returnsZero() {
        assertEquals(0, BargainCalculator.calcNextCut(1000, 500, 0, null, new Random()));
    }

    @Test
    void calcNextCut_floorReached_returnsZero() {
        assertEquals(0, BargainCalculator.calcNextCut(500, 500, 0, cfg(3, 1, 1, false), new Random()));
    }

    @Test
    void calcNextCut_lastCut_returnsRemaining() {
        // 已完成助力数 >= 目标，直接砍至底价
        assertEquals(200, BargainCalculator.calcNextCut(700, 500, 3, cfg(3, 1, 1, false), new Random()));
    }

    @Test
    void calcNextCut_evenWithinBounds() {
        // remaining=100, remainingAssists=4 -> even=25, 夹在 [10,100] 内
        assertEquals(25, BargainCalculator.calcNextCut(600, 500, 0, cfg(4, 10, 100, false), new Random()));
    }

    @Test
    void calcNextCut_evenClampedToMax() {
        // even=25 但 maxCut=20 -> 夹到 20
        assertEquals(20, BargainCalculator.calcNextCut(600, 500, 0, cfg(4, 10, 20, false), new Random()));
    }

    @Test
    void calcNextCut_evenClampedToMin() {
        // even=25 但 minCut=30 -> 夹到 30
        assertEquals(30, BargainCalculator.calcNextCut(600, 500, 0, cfg(4, 30, 100, false), new Random()));
    }

    @Test
    void calcNextCut_randomUpperLeLower_returnsLower() {
        // remaining=10, remainingAssists=2 -> maxByFuture = 10-(2-1)*minCut(10)=0
        // upper=min(100,0)=0, lower=min(10,0)=0 -> upper<=lower 返回 0
        assertEquals(0, BargainCalculator.calcNextCut(510, 500, 0, cfg(2, 10, 100, true), new Random()));
    }

    @Test
    void calcNextCut_random_normalWithinBounds() {
        // remaining=100, remainingAssists=3, min=10, max=100 -> maxByFuture=100-2*10=80
        // upper=min(100,80)=80, lower=min(10,80)=10
        long cut = BargainCalculator.calcNextCut(600, 500, 0, cfg(3, 10, 100, true), new Random(42));
        assertTrue(cut >= 10, "cut=" + cut);
        assertTrue(cut <= 80, "cut=" + cut);
    }
}
