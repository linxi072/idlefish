package com.idlefish.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权益兑换记录（ITER-F07 / REQ-07/09）：一次兑换一条，串联积分扣减流水与券发放结果。
 * <p>{@code exchangeNo}（BX 前缀）同时作为积分流水的 {@code bizId}，实现
 * 「兑换单 ↔ 积分流水 ↔ 用户券」三方对账（REQ-09）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_benefit_exchange")
public class BenefitExchange extends BaseEntity {

    /** 兑换成功。 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 兑换失败（已回滚，仅留痕）。 */
    public static final String STATUS_FAILED = "FAILED";

    private String exchangeNo;     // 兑换单号（BX 前缀，唯一）
    private Long benefitId;        // 权益 ID
    private String benefitCode;    // 权益编码快照
    private Long userId;           // 兑换用户
    private Long costPoints;       // 实际扣除积分
    private String benefitType;    // 权益类型快照
    private Long couponId;         // 券模板 ID 快照（COUPON 类型；实际用户券由 F-10 链路发放）
    private String status;         // SUCCESS / FAILED
    private String remark;         // 说明（失败原因 / 人工备注）
}
