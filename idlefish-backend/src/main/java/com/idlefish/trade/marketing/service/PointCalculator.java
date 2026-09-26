package com.idlefish.trade.marketing.service;

/**
 * 积分纯函数（无副作用，便于单测）。复用 F-10 券 {@code CouponService.calculateDiscount} 范式：
 * 所有计算返回确定值，不访问数据库 / 外部服务 / Spring 上下文。
 * <p>
 * 金额单位：分。积分兑换比例 {@code pointsPerYuan} 表示「多少积分抵 1 元（=100 分）」。
 */
public class PointCalculator {

    /** 积分抵现结果：实际抵扣积分与实际抵扣金额（分）。 */
    public static final class PointRedeemResult {
        public final long usedPoints;
        public final long discountFen;

        public PointRedeemResult(long usedPoints, long discountFen) {
            this.usedPoints = usedPoints;
            this.discountFen = discountFen;
        }
    }

    /**
     * 计算积分抵现：在用户可用积分、订单应付（分）与配置约束下，确定实际使用的积分与抵扣金额。
     * <ul>
     *   <li>使用积分不超过 请求值、可用值、以及「按最大抵扣比例换算出的积分上限」三者最小；</li>
     *   <li>抵扣金额（分）= 使用积分 * 100 / 兑换比例；</li>
     *   <li>抵扣金额不超过订单应付，且不超过 应付 * 最大抵扣比例。</li>
     * </ul>
     */
    public static PointRedeemResult calcRedeem(long requestedPoints, long availablePoints, long payableFen,
                                               int pointsPerYuan, double maxRedeemRatio) {
        if (requestedPoints <= 0 || availablePoints <= 0 || payableFen <= 0 || pointsPerYuan <= 0) {
            return new PointRedeemResult(0, 0);
        }
        // 1) 按比例换算允许的最大抵扣金额（分）
        long maxByRatio = (long) (payableFen * clampRatio(maxRedeemRatio));
        long maxDiscount = Math.min(maxByRatio, payableFen);
        if (maxDiscount <= 0) {
            return new PointRedeemResult(0, 0);
        }
        // 2) 由最大抵扣金额反推允许使用的最大积分数
        long maxPointsByDiscount = maxDiscount * pointsPerYuan / 100L;
        // 3) 实际可用积分取三者最小
        long used = Math.min(requestedPoints, Math.min(availablePoints, maxPointsByDiscount));
        if (used <= 0) {
            return new PointRedeemResult(0, 0);
        }
        long discount = used * 100L / pointsPerYuan;
        // 兜底：抵扣不超过应付与比例上限（数值取整后再夹一次）
        discount = Math.min(discount, maxDiscount);
        return new PointRedeemResult(used, discount);
    }

    /** 签到积分：基础分 + 连续天数加成（每天 +1，封顶 signinMaxBonus）。 */
    public static int calcSigninPoints(int signinBase, int consecutiveDays, int signinMaxBonus) {
        if (consecutiveDays <= 0) {
            return 0;
        }
        int bonus = Math.min(consecutiveDays - 1, Math.max(0, signinMaxBonus));
        return signinBase + bonus;
    }

    /** 交易得积分：每元（100 分）得 earnPointsPerYuan 积分，单笔封顶。 */
    public static long calcTradePoints(long payAmountFen, int earnPointsPerYuan, long tradeMaxPerOrder) {
        if (payAmountFen <= 0 || earnPointsPerYuan <= 0) {
            return 0;
        }
        long earned = payAmountFen * earnPointsPerYuan / 100L;
        if (tradeMaxPerOrder > 0 && earned > tradeMaxPerOrder) {
            earned = tradeMaxPerOrder;
        }
        return earned;
    }

    /** 评价得积分：固定值（非负）。 */
    public static int calcReviewPoints(int reviewFixed) {
        return Math.max(0, reviewFixed);
    }

    private static double clampRatio(double r) {
        if (r < 0) {
            return 0;
        }
        if (r > 1) {
            return 1;
        }
        return r;
    }
}
