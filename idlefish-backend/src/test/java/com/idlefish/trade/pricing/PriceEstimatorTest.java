package com.idlefish.trade.pricing;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 估价纯函数单测（REQ-07）：无状态、无浮点资损，离线可跑，覆盖 100% 分支。
 */
class PriceEstimatorTest {

    // ===================== 成色折损系数 =====================

    @Test
    void conditionCoefficient_levelBoundary() {
        assertEquals(1.0, PriceEstimator.conditionCoefficient(1));
        assertEquals(0.85, PriceEstimator.conditionCoefficient(2));
        assertEquals(0.70, PriceEstimator.conditionCoefficient(3));
        assertEquals(0.55, PriceEstimator.conditionCoefficient(4));
        assertEquals(0.40, PriceEstimator.conditionCoefficient(5));
        // 越界回退 0.5
        assertEquals(0.5, PriceEstimator.conditionCoefficient(99));
        assertEquals(0.5, PriceEstimator.conditionCoefficient(0));
    }

    // ===================== 样本清洗 IQR =====================

    @Test
    void cleanSamples_removesNullAndNonPositive() {
        List<Long> raw = Arrays.asList(100L, null, 0L, -5L, 200L);
        List<Long> cleaned = PriceEstimator.cleanSamples(raw);
        assertEquals(Arrays.asList(100L, 200L), cleaned);
    }

    @Test
    void cleanSamples_smallSampleReturnsAsIs() {
        // n < 4 无法稳健估计 IQR，原样返回（去 null/非正后）
        List<Long> raw = Arrays.asList(100L, 200L, 1000L);
        List<Long> cleaned = PriceEstimator.cleanSamples(raw);
        assertEquals(3, cleaned.size());
    }

    @Test
    void cleanSamples_removesOutlier() {
        // 300~360 紧密分布，5000 为离群；IQR 仅剔除 5000
        List<Long> raw = Arrays.asList(5000L, 300L, 310L, 320L, 330L, 340L, 350L, 360L);
        List<Long> cleaned = PriceEstimator.cleanSamples(raw);
        assertFalse(cleaned.contains(5000L), "离群值应被剔除");
        assertEquals(7, cleaned.size());
    }

    @Test
    void cleanSamples_nullInputSafe() {
        assertTrue(PriceEstimator.cleanSamples(null).isEmpty());
    }

    // ===================== 百分位 =====================

    @Test
    void percentile_emptyOrSingle() {
        assertEquals(0L, PriceEstimator.percentile(Collections.emptyList(), 0.5));
        assertEquals(42L, PriceEstimator.percentile(Collections.singletonList(42L), 0.5));
    }

    @Test
    void percentile_interpolation() {
        // [0,10,20,30] p=0.5 → rank=1.5 → 10 + 0.5*(20-10)=15
        List<Long> s = Arrays.asList(0L, 10L, 20L, 30L);
        assertEquals(15L, PriceEstimator.percentile(s, 0.5));
    }

    // ===================== 分位估价区间 =====================

    @Test
    void sampleRange_insufficientReturnsNull() {
        // 不足 MIN_SAMPLE_SIZE(5)
        assertNull(PriceEstimator.sampleRange(Arrays.asList(100L, 200L, 300L)));
    }

    @Test
    void sampleRange_sufficientReturnsTriple() {
        List<Long> samples = Arrays.asList(100L, 120L, 130L, 140L, 150L, 160L, 200L);
        long[] r = PriceEstimator.sampleRange(samples);
        assertNotNull(r);
        assertEquals(3, r.length);
        // 清洗后 7 个（IQR 不剔除）：p25=125, p50=140, p75=155
        assertEquals(125L, r[0]);
        assertEquals(140L, r[1]);
        assertEquals(155L, r[2]);
    }

    // ===================== 规则兜底 =====================

    @Test
    void ruleFallback_zeroBaseReturnsNull() {
        assertNull(PriceEstimator.ruleFallback(0L, 3));
        assertNull(PriceEstimator.ruleFallback(-10L, 3));
    }

    @Test
    void ruleFallback_newLevelNoSpread() {
        // 全新：base=100000，center=100000，±10% → 90000/100000/110000
        long[] r = PriceEstimator.ruleFallback(100000L, 1);
        assertArrayEquals(new long[]{90000L, 100000L, 110000L}, r);
    }

    @Test
    void ruleFallback_agedLevelAppliesCoefficient() {
        // 5 成：center=100000*0.4=40000，±10% → 36000/40000/44000
        long[] r = PriceEstimator.ruleFallback(100000L, 5);
        assertArrayEquals(new long[]{36000L, 40000L, 44000L}, r);
    }

    // ===================== 运营系数覆盖 =====================

    @Test
    void applyOverride_nullOrInvalidReturnsSame() {
        assertNull(PriceEstimator.applyOverride(null, 1.5));
        long[] r = {100L, 200L, 300L};
        assertArrayEquals(r, PriceEstimator.applyOverride(r, 0));
        assertArrayEquals(r, PriceEstimator.applyOverride(r, -1));
    }

    @Test
    void applyOverride_scalesWholeRange() {
        long[] r = {90000L, 100000L, 110000L};
        long[] out = PriceEstimator.applyOverride(r, 1.2);
        assertArrayEquals(new long[]{108000L, 120000L, 132000L}, out);
    }

    // ===================== 偏离判定 REQ-09 =====================

    @Test
    void checkDeviation_none() {
        // 区间 100~200，报价 150 在区间内 → NONE
        assertEquals(PriceEstimator.DEVIATION_NONE, PriceEstimator.checkDeviation(150L, 100L, 200L));
    }

    @Test
    void checkDeviation_low() {
        // 报价低于下限 50%（<50）→ LOW
        assertEquals(PriceEstimator.DEVIATION_LOW, PriceEstimator.checkDeviation(40L, 100L, 200L));
    }

    @Test
    void checkDeviation_high() {
        // 报价高于上限 100%（>400）→ HIGH
        assertEquals(PriceEstimator.DEVIATION_HIGH, PriceEstimator.checkDeviation(500L, 100L, 200L));
    }

    // ===================== 异常低价 REQ-10 =====================

    @Test
    void isAbnormalLow_trueWhenBelowHalf() {
        assertTrue(PriceEstimator.isAbnormalLow(40L, 100L));
    }

    @Test
    void isAbnormalLow_falseAtHalfBoundary() {
        // 恰好等于下限 50%（50）不算异常
        assertFalse(PriceEstimator.isAbnormalLow(50L, 100L));
        // 区间无效（min<=0）不算
        assertFalse(PriceEstimator.isAbnormalLow(10L, 0L));
        // 报价 0 不算
        assertFalse(PriceEstimator.isAbnormalLow(0L, 100L));
    }
}
