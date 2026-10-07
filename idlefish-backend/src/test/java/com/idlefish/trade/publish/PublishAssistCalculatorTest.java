package com.idlefish.trade.publish;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发布辅助纯函数单测（U01）：成色文案/系数、规则估价区间、草稿模板。
 */
class PublishAssistCalculatorTest {

    @Test
    void conditionLabel_allLevels() {
        assertEquals("全新", PublishAssistCalculator.conditionLabel(1));
        assertEquals("95新", PublishAssistCalculator.conditionLabel(2));
        assertEquals("9成新", PublishAssistCalculator.conditionLabel(3));
        assertEquals("8成新", PublishAssistCalculator.conditionLabel(4));
        assertEquals("功能完好", PublishAssistCalculator.conditionLabel(5));
        assertEquals("未知成色", PublishAssistCalculator.conditionLabel(null));
        assertEquals("其他成色", PublishAssistCalculator.conditionLabel(9));
    }

    @Test
    void conditionCoefficient_allLevels() {
        assertEquals(1.0, PublishAssistCalculator.conditionCoefficient(1));
        assertEquals(0.85, PublishAssistCalculator.conditionCoefficient(2));
        assertEquals(0.70, PublishAssistCalculator.conditionCoefficient(3));
        assertEquals(0.55, PublishAssistCalculator.conditionCoefficient(4));
        assertEquals(0.40, PublishAssistCalculator.conditionCoefficient(5));
        assertEquals(0.5, PublishAssistCalculator.conditionCoefficient(9)); // 默认兜底
    }

    @Test
    void suggestPriceRange_noBaseReturnsNull() {
        assertNull(PublishAssistCalculator.suggestPriceRange(0, 1), "base=0 无基准价 → null");
        assertNull(PublishAssistCalculator.suggestPriceRange(-100, 3), "负基准价 → null（边界2 降级）");
    }

    @Test
    void suggestPriceRange_hasBase_appliesCoefficientAndBand() {
        // level=1 全新：×1.0 → 100000，±10% → [90000, 110000]
        assertArrayEquals(new long[]{90_000L, 110_000L}, PublishAssistCalculator.suggestPriceRange(100_000L, 1));
        // level=3 9成新：×0.70 → 70000 → [63000, 77000]
        assertArrayEquals(new long[]{63_000L, 77_000L}, PublishAssistCalculator.suggestPriceRange(100_000L, 3));
        // level=5 功能完好：×0.40 → 40000 → [36000, 44000]
        assertArrayEquals(new long[]{36_000L, 44_000L}, PublishAssistCalculator.suggestPriceRange(100_000L, 5));
    }

    @Test
    void draftTitle_template() {
        assertEquals("【全新】手机 低价转让", PublishAssistCalculator.draftTitle("手机", 1));
        assertEquals("【9成新】闲置 低价转让", PublishAssistCalculator.draftTitle(null, 3), "类目为空兜底为闲置");
        assertEquals("【未知成色】手机 低价转让", PublishAssistCalculator.draftTitle("手机", null), "成色为空兜底为未知成色");
    }

    @Test
    void draftDescription_template_includesFields() {
        String desc = PublishAssistCalculator.draftDescription("手机", 1, "我的旧手机");
        assertTrue(desc.contains("品类：手机"));
        assertTrue(desc.contains("成色：全新"));
        assertTrue(desc.contains("标题：我的旧手机"), "传入标题时回显");
        assertTrue(desc.contains("请以实物为准"), "含免责尾注");

        String noTitle = PublishAssistCalculator.draftDescription("手机", 1, null);
        assertTrue(noTitle.contains("品类：手机"));
        assertTrue(!noTitle.contains("标题："), "无标题时不回显标题行");
    }
}
