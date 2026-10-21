package com.idlefish.trade.trade.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对账纯函数单测（ITER-T02）：覆盖金额/笔数差异、平账判定与逐笔下钻差异识别。
 * 纯函数无状态、long 分整型，离线可跑。
 */
class ReconcileCalculatorTest {

    @Test
    void amountDiff_is_actual_minus_expected() {
        assertEquals(20L, ReconcileCalculator.amountDiff(100L, 120L));
        assertEquals(-30L, ReconcileCalculator.amountDiff(100L, 70L));
        assertEquals(0L, ReconcileCalculator.amountDiff(100L, 100L));
    }

    @Test
    void countDiff_is_actual_minus_expected() {
        assertEquals(1, ReconcileCalculator.countDiff(2, 3));
        assertEquals(-1, ReconcileCalculator.countDiff(2, 1));
        assertEquals(0, ReconcileCalculator.countDiff(2, 2));
    }

    @Test
    void matched_requires_both_amount_and_count_equal() {
        assertTrue(ReconcileCalculator.matched(100, 100, 2, 2));
        assertFalse(ReconcileCalculator.matched(100, 90, 2, 2), "金额不一致应判不平");
        assertFalse(ReconcileCalculator.matched(100, 100, 2, 3), "笔数不一致应判不平");
    }

    @Test
    void diffByBizNo_flags_only_mismatches() {
        Map<String, Long> expected = Map.of("A", 100L, "B", 200L);
        Map<String, Long> actual = Map.of("A", 100L, "B", 180L, "C", 50L);

        List<ReconcileCalculator.ReconcileDiffInput> diffs =
                ReconcileCalculator.diffByBizNo("PAY", expected, actual);

        // A 平账不报；B 金额不符；C 平台侧缺失（实际多一笔）
        assertEquals(2, diffs.size());
        Map<String, ReconcileCalculator.ReconcileDiffInput> byBiz = diffs.stream()
                .collect(Collectors.toMap(ReconcileCalculator.ReconcileDiffInput::bizNo, d -> d));
        assertFalse(byBiz.containsKey("A"), "平账笔不应出现在差异列表");

        ReconcileCalculator.ReconcileDiffInput b = byBiz.get("B");
        assertEquals(200L, b.expectedAmount());
        assertEquals(180L, b.actualAmount());
        assertEquals(-20L, b.diffAmount());

        ReconcileCalculator.ReconcileDiffInput c = byBiz.get("C");
        assertEquals(0L, c.expectedAmount(), "平台侧缺失，期望金额视为 0");
        assertEquals(50L, c.actualAmount());
        assertEquals(1, c.actualCount());
        assertEquals(0, c.expectedCount());
    }

    @Test
    void diffByBizNo_empty_when_all_match() {
        Map<String, Long> m = Map.of("A", 100L, "B", 200L);
        assertTrue(ReconcileCalculator.diffByBizNo("REFUND", m, m).isEmpty());
    }

    @Test
    void dimension_constants_align_with_fund_flow_type() {
        Set<String> dims = Set.of(ReconcileCalculator.DIM_PAY,
                ReconcileCalculator.DIM_REFUND, ReconcileCalculator.DIM_SETTLE);
        assertEquals(3, dims.size());
    }
}
