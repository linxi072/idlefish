package com.idlefish.trade.member.vo;

import lombok.Data;

/**
 * 权益兑换记录 VO（ITER-F07 / REQ-09）：串联兑换单号、积分扣减与发放结果，支持对账。
 */
@Data
public class BenefitExchangeVO {

    private Long id;
    private String exchangeNo;      // 兑换单号（BX 前缀）
    private Long benefitId;
    private String benefitCode;
    private String benefitName;     // 由权益目录派生（快照缺失时回填）
    private Long userId;
    private Long costPoints;        // 实际扣除积分
    private String benefitType;
    private Long couponId;          // 券模板 ID 快照
    private String status;          // SUCCESS / FAILED
    private String remark;
    private String createdAt;
}
