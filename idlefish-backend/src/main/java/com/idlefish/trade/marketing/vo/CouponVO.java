package com.idlefish.trade.marketing.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券模板视图（领券中心 / 后台列表）。
 */
@Data
public class CouponVO {

    private Long id;
    private String name;
    private String type;
    private Long thresholdAmount;    // 分
    private Long reduceAmount;       // 分
    private Double discountRate;
    private Long maxDiscountAmount;  // 分
    private String scope;
    private Long scopeId;
    private Integer totalCount;
    private Integer claimedCount;
    private Integer perUserLimit;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    /** 领券中心附加：当前用户是否已领取（claimedCount 满 / 已领过 时前端据此禁用按钮）。 */
    private Boolean claimed;
    /** 领券中心附加：是否还可领（库存 + 时间 + 状态综合判定）。 */
    private Boolean claimable;
}
