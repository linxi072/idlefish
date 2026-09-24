package com.idlefish.trade.user.service;

import com.idlefish.trade.common.enums.CreditLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 信用评分纯函数单测（F-06）：无 Spring、无 DB，确定性验证评分公式与等级边界。
 */
class CreditScoringTest {

    private final CreditCalculator calc = new CreditCalculator(null); // 使用默认权重

    @Test
    @DisplayName("新用户（无实名/无订单/无评价）基线 = 好评基线分 15 → 较差")
    void newUserBaseline() {
        CreditCalculator.CreditFactors f = new CreditCalculator.CreditFactors();
        int s = calc.compute(f);
        assertEquals(15, s);
        assertEquals(CreditLevel.POOR, CreditLevel.fromScore(s));
    }

    @Test
    @DisplayName("优质用户（实名+老用户+全履约+全好评）= 100 → 优秀")
    void fullGoodUser() {
        CreditCalculator.CreditFactors f = new CreditCalculator.CreditFactors();
        f.setRealNameVerified(true);
        f.setAccountAgeDays(400);            // 超 300 天，封顶 +20
        f.setTotalOrders(10);
        f.setCompletedOrders(10);            // 履约率 100% → +40
        f.setTotalReviews(5);
        f.setGoodReviews(5);                 // 好评率 100% → +30
        int s = calc.compute(f);
        assertEquals(10 + 20 + 40 + 30, s);
        assertEquals(CreditLevel.EXCELLENT, CreditLevel.fromScore(s));
    }

    @Test
    @DisplayName("封禁用户：满分扣 30 = 70 → 一般")
    void bannedClamped() {
        CreditCalculator.CreditFactors f = new CreditCalculator.CreditFactors();
        f.setRealNameVerified(true);
        f.setAccountAgeDays(400);
        f.setTotalOrders(10);
        f.setCompletedOrders(10);
        f.setTotalReviews(5);
        f.setGoodReviews(5);
        f.setBanned(true);
        int s = calc.compute(f);
        assertEquals(70, s);
        assertEquals(CreditLevel.FAIR, CreditLevel.fromScore(s));
    }

    @Test
    @DisplayName("低履约低好评：20% 履约→8，25% 好评→8，合计 16 → 较差")
    void lowFulfillment() {
        CreditCalculator.CreditFactors f = new CreditCalculator.CreditFactors();
        f.setTotalOrders(10);
        f.setCompletedOrders(2);             // 20% → 40*0.2 = 8
        f.setTotalReviews(4);
        f.setGoodReviews(1);                 // 25% → 30*0.25 = 7.5 → 8
        int s = calc.compute(f);
        assertEquals(16, s);
        assertEquals(CreditLevel.POOR, CreditLevel.fromScore(s));
    }

    @Test
    @DisplayName("等级边界：90/89/75/74/60/59")
    void levelBoundaries() {
        assertEquals(CreditLevel.EXCELLENT, CreditLevel.fromScore(90));
        assertEquals(CreditLevel.GOOD, CreditLevel.fromScore(89));
        assertEquals(CreditLevel.GOOD, CreditLevel.fromScore(75));
        assertEquals(CreditLevel.FAIR, CreditLevel.fromScore(74));
        assertEquals(CreditLevel.FAIR, CreditLevel.fromScore(60));
        assertEquals(CreditLevel.POOR, CreditLevel.fromScore(59));
    }
}
