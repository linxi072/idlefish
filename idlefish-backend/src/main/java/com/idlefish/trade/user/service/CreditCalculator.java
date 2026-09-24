package com.idlefish.trade.user.service;

import lombok.Data;
import org.springframework.util.Assert;

/**
 * 信用分计算（F-06，纯函数，无数据库依赖，便于单元测试）。
 *
 * 评分构成（满分 100，clamp [0,100]）：
 * <pre>
 *   实名认证        +realName（默认 10）
 *   注册时长        +min(ageDays/30 * agePer30d, ageCap)（默认每满 30 天 +2，封顶 20）
 *   履约率          +round(fulfillment * completedOrders/totalOrders)（默认权重 40，无订单计 0）
 *   好评率          +round(review * goodReviews/totalReviews)（默认权重 30；无评价给 reviewBaseline=15）
 *   风控扣分        -(banned?banPenalty:0 + min(highRiskCount*riskPerEvent, riskCap))
 * </pre>
 */
public class CreditCalculator {

    @Data
    public static class CreditWeights {
        private int realName = 10;
        private int agePer30d = 2;
        private int ageCap = 20;
        private int fulfillment = 40;
        private int review = 30;
        private int reviewBaseline = 15;
        private int banPenalty = 30;
        private int riskPerEvent = 5;
        private int riskCap = 30;

        public CreditWeights() {
        }

        public CreditWeights(int realName, int agePer30d, int ageCap, int fulfillment,
                             int review, int reviewBaseline, int banPenalty, int riskPerEvent, int riskCap) {
            this.realName = realName;
            this.agePer30d = agePer30d;
            this.ageCap = ageCap;
            this.fulfillment = fulfillment;
            this.review = review;
            this.reviewBaseline = reviewBaseline;
            this.banPenalty = banPenalty;
            this.riskPerEvent = riskPerEvent;
            this.riskCap = riskCap;
        }
    }

    @Data
    public static class CreditFactors {
        private boolean realNameVerified;
        private int accountAgeDays;
        private int completedOrders;
        private int totalOrders;
        private int goodReviews;
        private int totalReviews;
        private boolean banned;
        private int highRiskCount;
    }

    public static final CreditWeights DEFAULT = new CreditWeights();

    private final CreditWeights weights;

    public CreditCalculator(CreditWeights weights) {
        this.weights = weights == null ? DEFAULT : weights;
        Assert.isTrue(this.weights.getAgePer30d() >= 0 && this.weights.getAgeCap() >= 0, "age weights must be >=0");
    }

    public int compute(CreditFactors f) {
        int score = 0;
        if (f.isRealNameVerified()) {
            score += weights.getRealName();
        }
        score += Math.min((f.getAccountAgeDays() / 30) * weights.getAgePer30d(), weights.getAgeCap());

        if (f.getTotalOrders() > 0) {
            double rate = (double) f.getCompletedOrders() / f.getTotalOrders();
            score += (int) Math.round(weights.getFulfillment() * rate);
        }

        if (f.getTotalReviews() > 0) {
            double rate = (double) f.getGoodReviews() / f.getTotalReviews();
            score += (int) Math.round(weights.getReview() * rate);
        } else {
            score += weights.getReviewBaseline();
        }

        int penalty = (f.isBanned() ? weights.getBanPenalty() : 0)
                + Math.min(f.getHighRiskCount() * weights.getRiskPerEvent(), weights.getRiskCap());
        score -= penalty;

        if (score < 0) {
            score = 0;
        }
        if (score > 100) {
            score = 100;
        }
        return score;
    }
}
