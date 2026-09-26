package com.idlefish.trade.search.service;

import com.idlefish.trade.item.entity.Item;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 个性化推荐打分器（纯函数，无副作用，便于离线单测）。
 * 综合三部分信号：
 * ① 热度分（浏览/收藏/点赞，按上架时间半衰期衰减）；
 * ② 行为加权（用户历史浏览/收藏/下单/支付沉淀的类目兴趣向量）；
 * ③ 冷启动保量（上架不久的新品加固定保量分，确保进入推荐流）。
 */
public final class RecommendScorer {

    /** 热度半衰期（天）：每过 7 天热度衰减一半。 */
    public static final double HALF_LIFE_DAYS = 7.0;
    /** 冷启动窗口（天）：上架不超过该天数的新品获得保量加分。 */
    public static final int COLD_START_MAX_AGE_DAYS = 3;
    /** 冷启动保量加分。 */
    public static final double COLD_START_BONUS = 500.0;
    /** 同城加成。 */
    public static final double SAME_CITY_BONUS = 1000.0;

    private RecommendScorer() {
    }

    /** 热度分：浏览*1 + 收藏*5 + 点赞*3，按上架时间半衰期衰减。 */
    public static double heatScore(Item it, LocalDateTime now) {
        int view = it.getViewCount() == null ? 0 : it.getViewCount();
        int fav = it.getFavCount() == null ? 0 : it.getFavCount();
        int like = it.getLikeCount() == null ? 0 : it.getLikeCount();
        double base = view + fav * 5.0 + like * 3.0;
        long ageDays = it.getCreatedAt() == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(it.getCreatedAt(), now));
        double decay = Math.pow(0.5, ageDays / HALF_LIFE_DAYS);
        return base * decay;
    }

    /** 行为加权：返回商品所属类目在用户兴趣向量中的权重（无兴趣则返回 0）。 */
    public static double interestBoost(Item it, Map<Long, Double> categoryWeights) {
        if (categoryWeights == null || it.getCategoryId() == null) {
            return 0.0;
        }
        Double w = categoryWeights.get(it.getCategoryId());
        return w == null ? 0.0 : w;
    }

    /** 冷启动保量：上架不超过窗口天数的新品加固定保量分；否则 0。 */
    public static double coldStartBoost(Item it, LocalDateTime now) {
        if (it.getCreatedAt() == null) {
            return 0.0;
        }
        long ageDays = ChronoUnit.DAYS.between(it.getCreatedAt(), now);
        if (ageDays >= 0 && ageDays <= COLD_START_MAX_AGE_DAYS) {
            return COLD_START_BONUS;
        }
        return 0.0;
    }

    /** 综合分：热度 ×(1+兴趣) + 同城加成 + 冷启动保量。 */
    public static double finalScore(double heat, double interest, boolean sameCity, double coldBoost) {
        return heat * (1.0 + interest) + (sameCity ? SAME_CITY_BONUS : 0.0) + coldBoost;
    }

    /** 埋点事件 → 兴趣权重（浏览1 / 收藏3 / 下单5 / 支付4；其余 0）。 */
    public static double eventWeight(String event) {
        if (event == null) {
            return 0.0;
        }
        switch (event) {
            case "view_item":    return 1.0;
            case "favorite":     return 3.0;
            case "order_create": return 5.0;
            case "pay":          return 4.0;
            default:             return 0.0;
        }
    }
}
