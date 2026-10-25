package com.idlefish.trade.member.vo;

import lombok.Data;

/**
 * 权益视图（ITER-F07）：权益目录 + 当前用户的可兑换判定，供用户端兑换中心展示。
 */
@Data
public class BenefitVO {

    private Long id;
    private String benefitCode;
    private String benefitName;
    private String benefitType;
    private String description;
    private Long costPoints;          // 所需积分（可消耗）
    private String minLevelCode;      // 最低等级门槛
    private Long voucherAmountFen;    // 金额类权益面额（分）
    private Integer stock;            // 剩余库存
    private Integer dailyLimit;       // 每日兑换上限（0=不限）
    private String onlineAt;          // 上架时间
    private String offlineAt;         // 下架时间
    private Integer sortOrder;

    // ===== 派生判定（服务端计算，前端直接渲染，避免多接口往返）=====
    private boolean levelSatisfied;   // 等级是否达标（REQ-07）
    private boolean pointsEnough;     // 积分是否充足
    private boolean stockEnough;      // 库存是否充足（REQ-08）
    private boolean exchangeable;     // 综合是否可兑换（等级 + 积分 + 库存 + 时间窗）
    private Integer todayExchanged;   // 今日已兑换次数
}
