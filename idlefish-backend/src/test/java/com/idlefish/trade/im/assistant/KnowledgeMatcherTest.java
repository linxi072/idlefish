package com.idlefish.trade.im.assistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识库匹配纯函数单测（ITER-F06 / REQ-11 品类优先 + REQ-03 置信度）。
 */
class KnowledgeMatcherTest {

    private static final KnowledgeMatcher.Candidate CATEGORY_A = new KnowledgeMatcher.Candidate(
            1L, 100L, ImIntent.SHIPPING, List.of("发货", "什么时候发"), "品类A：付款后48小时内发货", 0);

    private static final KnowledgeMatcher.Candidate GENERAL = new KnowledgeMatcher.Candidate(
            2L, 0L, ImIntent.SHIPPING,
            List.of("发货", "什么时候发"), "通用：付款后48小时内发货", 0);

    @Test
    void categoryEntryWinsOverGeneral() {
        KnowledgeMatcher.MatchResult r = KnowledgeMatcher.match(
                List.of(GENERAL, CATEGORY_A), ImIntent.SHIPPING, 100L, "什么时候发货");
        assertTrue(r.hit());
        assertEquals(1L, r.candidate().id());
        assertTrue(r.categoryHit());
        assertEquals(0.95, r.confidence(), 1e-9);
    }

    @Test
    void generalFallbackWhenNoCategoryMatch() {
        KnowledgeMatcher.MatchResult r = KnowledgeMatcher.match(
                List.of(GENERAL, CATEGORY_A), ImIntent.SHIPPING, 999L, "什么时候发货");
        assertTrue(r.hit());
        assertEquals(2L, r.candidate().id());
        assertFalse(r.categoryHit());
        assertEquals(0.75, r.confidence(), 1e-9);
    }

    @Test
    void singleKeywordGivesLowConfidence() {
        KnowledgeMatcher.MatchResult r = KnowledgeMatcher.match(
                List.of(GENERAL), ImIntent.SHIPPING, null, "啥时候发货");
        assertTrue(r.hit());
        assertEquals(0.55, r.confidence(), 1e-9);
    }

    @Test
    void noMatchForOtherIntentOrUnknown() {
        assertFalse(KnowledgeMatcher.match(
                List.of(GENERAL), ImIntent.REFUND, null, "什么时候发货").hit());
        assertFalse(KnowledgeMatcher.match(
                List.of(GENERAL), ImIntent.UNKNOWN, null, "发货").hit());
        assertFalse(KnowledgeMatcher.match(List.of(), ImIntent.SHIPPING, null, "发货").hit());
    }
}
