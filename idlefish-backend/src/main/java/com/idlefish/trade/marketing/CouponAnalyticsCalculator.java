package com.idlefish.trade.marketing;

/**
 * 券营销派生指标计算（F-13.5）——纯函数，无 DB / 无 Spring 依赖，离线可单测。
 * <p>所有比率返回 0.0~1.0（核销率）或任意非负倍数（ROI）；分母为 0 时统一返回 0.0，避免 NaN / 除零。</p>
 */
public final class CouponAnalyticsCalculator {

    private CouponAnalyticsCalculator() {
    }

    /** 核销率 = 核销券数 / 发放（领取）券数。 */
    public static double redemptionRate(long issued, long redeemed) {
        if (issued <= 0) {
            return 0.0;
        }
        return (double) redeemed / issued;
    }

    /**
     * 营销 ROI =（核销带来的成交额 - 优惠成本）/ 优惠成本。
     * 优惠成本 <= 0（无核销优惠）时返回 0.0。
     */
    public static double roi(long redeemedGmv, long discountCost) {
        if (discountCost <= 0) {
            return 0.0;
        }
        return (double) (redeemedGmv - discountCost) / discountCost;
    }
}
