package com.idlefish.trade.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户优惠券（领取后落库）。金额单位：分。
 * status: UNUSED(未使用) / USED(已核销) / EXPIRED(已过期)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_coupon")
public class UserCoupon extends BaseEntity {

    private Long couponId;           // 关联 t_coupon.id
    private Long userId;             // 持有用户
    private String orderNo;          // 核销绑定的订单号（未使用为 null）
    private String status;           // UNUSED / USED / EXPIRED
    private LocalDateTime expireAt;  // 过期时间（领取时快照券 end_at）
    private LocalDateTime claimedAt; // 领取时间
    private LocalDateTime usedAt;    // 核销时间
}
