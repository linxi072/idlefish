package com.idlefish.trade.member.vo;

import lombok.Data;

/**
 * 成长值账户视图（ITER-F07 / REQ-11 用户端可见）：等级、权益、升级进度、衰减预警一屏展示。
 * <p>与 F-13.2 {@link MemberLevelView} 的区别：本视图叠加 ITER-F07 深化字段
 * （累计获得、冻结状态、距衰减天数、规则版本），是「我的成长中心」唯一数据源。</p>
 */
@Data
public class GrowthAccountVO {

    private String levelCode;            // 当前等级编码
    private String levelName;            // 当前等级名称
    private String icon;                 // 等级图标
    private String color;                // 主题色
    private Integer growthValue;         // 当前成长值（不可消耗，REQ-01）
    private Integer totalEarned;         // 累计获得成长值（对账口径，REQ-09）
    private Integer freeShipping;        // 是否免运费
    private Integer priorityReview;      // 是否优先审核
    private Integer commissionDiscount;  // 平台佣金减免（千分比）
    private String nextLevelName;        // 下一级名称（顶级为 null）
    private Integer growthToNext;        // 距下一级所需成长值
    private Integer percent;             // 当前级内进度（0~100）
    private Boolean frozen;              // 发放通道是否冻结（REQ-03）
    private Integer daysUntilDecay;      // 距进入不活跃衰减的天数（REQ-05；0=已在衰减期）
    private Integer decayNotifyDays;     // 衰减提前通知天数配置（提前 N 日预警）
    private String ruleVersion;          // 当前规则版本（REQ-14）
}
