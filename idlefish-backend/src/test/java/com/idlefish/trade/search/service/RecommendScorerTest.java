package com.idlefish.trade.search.service;

import com.idlefish.trade.item.entity.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RecommendScorer 纯函数单测（离线可跑）：热度衰减、行为加权、冷启动保量、事件权重。
 */
class RecommendScorerTest {

    private Item item(long view, long fav, long like, LocalDateTime createdAt, Long categoryId, String city) {
        Item it = new Item();
        it.setViewCount((int) view);
        it.setFavCount((int) fav);
        it.setLikeCount((int) like);
        it.setCreatedAt(createdAt);
        it.setCategoryId(categoryId);
        it.setCity(city);
        return it;
    }

    @Test
    @DisplayName("heatScore：上架越久衰减越多")
    void heatScoreDecay() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 26, 12, 0);
        Item fresh = item(2, 0, 0, now, 1L, "BJ");
        Item old = item(2, 0, 0, now.minusDays(14), 1L, "BJ");
        // fresh 衰减 1.0；old 衰减 0.5^(14/7)=0.25
        assertTrue(RecommendScorer.heatScore(fresh, now) > RecommendScorer.heatScore(old, now));
        assertEquals(2.0, RecommendScorer.heatScore(fresh, now), 1e-9);
        assertEquals(0.5, RecommendScorer.heatScore(old, now), 1e-9);
    }

    @Test
    @DisplayName("coldStartBoost：窗口内新品加分，超窗或空时间返回 0")
    void coldStartBoost() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 26, 12, 0);
        Item fresh = item(0, 0, 0, now.minusDays(1), 1L, "BJ");
        Item old = item(0, 0, 0, now.minusDays(10), 1L, "BJ");
        Item noTime = item(0, 0, 0, null, 1L, "BJ");
        assertEquals(RecommendScorer.COLD_START_BONUS, RecommendScorer.coldStartBoost(fresh, now), 1e-9);
        assertEquals(0.0, RecommendScorer.coldStartBoost(old, now), 1e-9);
        assertEquals(0.0, RecommendScorer.coldStartBoost(noTime, now), 1e-9);
    }

    @Test
    @DisplayName("interestBoost：命中类目权重，否则 0")
    void interestBoost() {
        Map<Long, Double> weights = new HashMap<>();
        weights.put(5L, 2.0);
        Item hit = item(0, 0, 0, null, 5L, "BJ");
        Item miss = item(0, 0, 0, null, 9L, "BJ");
        assertEquals(2.0, RecommendScorer.interestBoost(hit, weights), 1e-9);
        assertEquals(0.0, RecommendScorer.interestBoost(miss, weights), 1e-9);
    }

    @Test
    @DisplayName("finalScore：同城/兴趣/冷启动均提升综合分")
    void finalScoreOrdering() {
        double base = RecommendScorer.finalScore(10.0, 0.0, false, 0.0);
        double withInterest = RecommendScorer.finalScore(10.0, 1.0, false, 0.0);
        double withCity = RecommendScorer.finalScore(10.0, 0.0, true, 0.0);
        double withCold = RecommendScorer.finalScore(10.0, 0.0, false, RecommendScorer.COLD_START_BONUS);
        assertTrue(withInterest > base);
        assertTrue(withCity > base);
        assertTrue(withCold > base);
    }

    @Test
    @DisplayName("eventWeight：浏览1/收藏3/下单5/支付4/其它0")
    void eventWeight() {
        assertEquals(1.0, RecommendScorer.eventWeight("view_item"), 1e-9);
        assertEquals(3.0, RecommendScorer.eventWeight("favorite"), 1e-9);
        assertEquals(5.0, RecommendScorer.eventWeight("order_create"), 1e-9);
        assertEquals(4.0, RecommendScorer.eventWeight("pay"), 1e-9);
        assertEquals(0.0, RecommendScorer.eventWeight("unknown"), 1e-9);
        assertEquals(0.0, RecommendScorer.eventWeight(null), 1e-9);
    }
}
