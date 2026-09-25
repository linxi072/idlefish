package com.idlefish.trade.marketing.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券视图（我的券 / 下单可用券）。
 */
@Data
public class UserCouponVO {

    private Long id;                // user_coupon.id
    private Long couponId;
    private String name;
    private String type;
    private Long thresholdAmount;   // 分
    private Long reduceAmount;      // 分
    private Double discountRate;
    private Long maxDiscountAmount; // 分
    private String scope;
    private Long scopeId;
    private String status;          // UNUSED / USED / EXPIRED
    private String orderNo;
    private LocalDateTime expireAt;

    /** 下单可用券附加：对当前订单应付（分）可抵扣金额，0 表示不满足门槛。 */
    private Long discountAmount;
}
