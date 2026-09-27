package com.idlefish.trade.marketing.vo;

import lombok.Data;

/**
 * 券营销概览（F-13.5）。金额字段单位：分。
 */
@Data
public class CouponAnalyticsOverview {

    /** 发放（领取）券总数。 */
    private Long issuedCount;
    /** 已核销券数。 */
    private Long redeemedCount;
    /** 核销率 = redeemedCount / issuedCount（0~1）。 */
    private Double redemptionRate;
    /** 核销关联订单数（去重）。 */
    private Long redeemedOrderCount;
    /** 核销带来的成交额（分，关联 t_order.pay_amount 求和）。 */
    private Long redeemedGmv;
    /** 核销优惠成本（分，t_user_coupon.discount_amount 求和）。 */
    private Long discountCost;
    /** 营销 ROI =（redeemedGmv - discountCost）/ discountCost。 */
    private Double roi;
}
