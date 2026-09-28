package com.idlefish.trade.dispute.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 售后维权工单（F-17）：买家发起争议 → 卖家举证 → 平台介入 → 裁决。
 * 金额单位：分。状态流转见 {@link com.idlefish.trade.dispute.DisputeStateMachine}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_dispute")
public class Dispute extends BaseEntity {

    private String disputeNo;       // 工单号（唯一）
    private String orderNo;         // 关联订单号
    private Long buyerId;           // 申请人（买家）
    private Long sellerId;          // 被申诉方（卖家）
    private String type;            // 争议类型：REFUND_REJECTED/NOT_RECEIVED/DAMAGED/NOT_AS_DESC
    private String expectation;     // 诉求：REFUND 仅退款 / RETURN_REFUND 退货退款
    private String reason;          // 原因描述
    private Long amount;            // 争议金额（分）
    private String status;          // PENDING/SELLER_REPLIED/PLATFORM/RESOLVED/CLOSED/CANCELED
    private String buyerEvidence;   // 买家举证（图片 URL，逗号分隔）
    private String sellerEvidence;  // 卖家举证（图片 URL，逗号分隔）
    private String result;          // 裁决结果：BUYER_WIN/SELLER_WIN/PARTIAL
    private String platformRemark;  // 平台裁决说明
    private Long refundAmount;      // 裁决退款金额（分）；0 表示不支持退款
    private LocalDateTime handledAt; // 处理 / 裁决时间
}
