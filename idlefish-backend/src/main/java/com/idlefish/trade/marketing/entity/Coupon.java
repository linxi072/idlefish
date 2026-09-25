package com.idlefish.trade.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 优惠券模板（平台营销发放）。金额单位：分。
 * type: FULL_REDUCTION(满减) / NO_THRESHOLD(无门槛) / DISCOUNT(折扣)
 * scope: ALL(全场) / CATEGORY(指定类目) / ITEM(指定商品)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_coupon")
public class Coupon extends BaseEntity {

    private String name;             // 券名称
    private String type;             // 券类型：FULL_REDUCTION / NO_THRESHOLD / DISCOUNT
    private Long thresholdAmount;    // 满减门槛（分），无门槛券为 0
    private Long reduceAmount;       // 减免金额（分），用于满减/无门槛
    private Double discountRate;     // 折扣率（如 0.9 表示 9 折），折扣券专用
    private Long maxDiscountAmount;  // 折扣封顶减免（分），折扣券可选
    private String scope;            // 适用范围：ALL / CATEGORY / ITEM
    private Long scopeId;            // 适用类目/商品 ID（scope=ALL 时为 null）
    private Integer totalCount;      // 发放总量
    private Integer claimedCount;    // 已领取数
    private Integer perUserLimit;    // 每人限领（v1 实际按 1 张/人 校验）
    private String status;           // ACTIVE / PAUSED / ENDED
    private LocalDateTime startAt;   // 生效时间
    private LocalDateTime endAt;     // 失效时间
}
