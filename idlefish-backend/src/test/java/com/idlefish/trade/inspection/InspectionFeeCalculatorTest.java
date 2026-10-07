package com.idlefish.trade.inspection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鉴定验货费用与建议纯函数单测（F-02）：覆盖服务费 clamp 边界与高价值建议（REQ-08/REQ-10）。
 */
class InspectionFeeCalculatorTest {

    @Test
    void calcServiceFee_clamped() {
        assertEquals(0L, InspectionFeeCalculator.calcServiceFee(0));
        assertEquals(0L, InspectionFeeCalculator.calcServiceFee(-100));
        // 100 元 = 10000 分 → 200 分 < 最低 500 分，取下限
        assertEquals(InspectionFeeCalculator.MIN_FEE_FEN, InspectionFeeCalculator.calcServiceFee(10_000));
        // 2000 元 = 200000 分 → 4000 分，介于上下限之间
        assertEquals(4_000L, InspectionFeeCalculator.calcServiceFee(200_000));
        // 10000 元 = 1000000 分 → 20000 分，恰为上限
        assertEquals(InspectionFeeCalculator.MAX_FEE_FEN, InspectionFeeCalculator.calcServiceFee(1_000_000));
        // 20000 元 = 2000000 分 → 40000 分，超出上限，取上限
        assertEquals(InspectionFeeCalculator.MAX_FEE_FEN, InspectionFeeCalculator.calcServiceFee(2_000_000));
    }

    @Test
    void suggestInspection_byThreshold_and_category() {
        assertTrue(InspectionFeeCalculator.suggestInspection(200_000, "BOOK"));   // 命中价格阈值
        assertTrue(InspectionFeeCalculator.suggestInspection(10_000, "3C"));      // 命中高价值品类
        assertFalse(InspectionFeeCalculator.suggestInspection(10_000, "BOOK"));   // 普通品类低价
        assertFalse(InspectionFeeCalculator.suggestInspection(10_000, null));     // 无品类
    }
}
