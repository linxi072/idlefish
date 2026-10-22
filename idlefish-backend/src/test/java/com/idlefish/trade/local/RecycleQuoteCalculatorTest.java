package com.idlefish.trade.local;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回收估价纯函数单测（ITER-F05 / REQ-02、REQ-04）：成色系数、上下限保护、跌价判定。
 */
class RecycleQuoteCalculatorTest {

    private static Map<Integer, Double> ratios() {
        Map<Integer, Double> m = new HashMap<>();
        m.put(1, 0.85);
        m.put(2, 0.75);
        m.put(3, 0.62);
        m.put(4, 0.48);
        m.put(5, 0.35);
        return m;
    }

    @Test
    @DisplayName("REQ-02 参考价 × 成色系数：9成新 100000 分 → 62000 分")
    void estimate_byConditionRatio() {
        long amount = RecycleQuoteCalculator.estimate(100_000L, 3, ratios(), 0.6, 100L);
        assertEquals(62_000L, amount);
    }

    @Test
    @DisplayName("REQ-02 全新档位系数最高")
    void estimate_newIsHighest() {
        assertEquals(85_000L, RecycleQuoteCalculator.estimate(100_000L, 1, ratios(), 0.6, 100L));
    }

    @Test
    @DisplayName("估价低于下限时抬升到 minFen，避免 0 元单")
    void estimate_belowMinClampedUp() {
        long amount = RecycleQuoteCalculator.estimate(200L, 5, ratios(), 0.6, 100L);
        // 200 * 0.35 = 70 → 低于 min 100，抬升到 100
        assertEquals(100L, amount);
    }

    @Test
    @DisplayName("预估价永不超过参考价（上限保护）")
    void estimate_neverAboveReference() {
        Map<Integer, Double> m = new HashMap<>();
        m.put(3, 1.5);
        long amount = RecycleQuoteCalculator.estimate(10_000L, 3, m, 1.5, 0L);
        assertEquals(10_000L, amount);
    }

    @Test
    @DisplayName("无参考价返回 0（明确无数据，不编造价格）")
    void estimate_zeroReference_returnsZero() {
        assertEquals(0L, RecycleQuoteCalculator.estimate(0L, 3, ratios(), 0.6, 100L));
        assertEquals(0L, RecycleQuoteCalculator.estimate(-5L, 3, ratios(), 0.6, 100L));
    }

    @Test
    @DisplayName("成色未命中系数时回落 defaultRatio")
    void estimate_fallbackToDefaultRatio() {
        // level 9 不在映射中 → 用 defaultRatio 0.6
        assertEquals(60_000L, RecycleQuoteCalculator.estimate(100_000L, 9, ratios(), 0.6, 100L));
    }

    @Test
    @DisplayName("null 成色 / null 映射时用兜底系数，不抛 NPE")
    void estimate_nullSafe() {
        assertEquals(60_000L, RecycleQuoteCalculator.estimate(100_000L, null, null, 0.6, 100L));
    }

    // ===== 封顶 =====

    @Test
    @DisplayName("REQ-04 服务端二次封顶：报价不得超过预估价 × 1.2")
    void capFinalAmount_bounded() {
        long capped = RecycleQuoteCalculator.capFinalAmount(999_999L, 10_000L);
        assertEquals(12_000L, capped);
    }

    @Test
    @DisplayName("REQ-04 报价为空 / 非正时回落预估价，不产生异常金额")
    void capFinalAmount_nullOrNonPositive() {
        assertEquals(10_000L, RecycleQuoteCalculator.capFinalAmount(null, 10_000L));
        assertEquals(10_000L, RecycleQuoteCalculator.capFinalAmount(0L, 10_000L));
        assertEquals(10_000L, RecycleQuoteCalculator.capFinalAmount(-1L, 10_000L));
    }

    @Test
    @DisplayName("预估价为 0 时禁止产生正价（返回 0 由上层拦截）")
    void capFinalAmount_zeroEstimate() {
        assertEquals(0L, RecycleQuoteCalculator.capFinalAmount(5_000L, 0L));
    }

    // ===== 跌幅判定 =====

    @Test
    @DisplayName("跌幅计算：10000 → 5000 为 0.5")
    void dropRatio_half() {
        assertEquals(0.5d, RecycleQuoteCalculator.dropRatio(10_000L, 5_000L), 1e-9);
    }

    @Test
    @DisplayName("涨价或持平跌幅为 0")
    void dropRatio_zeroWhenNotDropped() {
        assertEquals(0d, RecycleQuoteCalculator.dropRatio(10_000L, 12_000L), 1e-9);
        assertEquals(0d, RecycleQuoteCalculator.dropRatio(10_000L, 10_000L), 1e-9);
        assertEquals(0d, RecycleQuoteCalculator.dropRatio(0L, 100L), 1e-9);
    }

    @Test
    @DisplayName("REQ-04 跌幅超阈值判定为异常调价，需人工复核")
    void abnormalDrop_flagsOverThreshold() {
        assertTrue(RecycleQuoteCalculator.abnormalDrop(10_000L, 4_000L, 0.5));
        assertFalse(RecycleQuoteCalculator.abnormalDrop(10_000L, 6_000L, 0.5));
        // 阈值边界：跌幅恰好等于阈值不算异常
        assertFalse(RecycleQuoteCalculator.abnormalDrop(10_000L, 5_000L, 0.5));
    }
}
