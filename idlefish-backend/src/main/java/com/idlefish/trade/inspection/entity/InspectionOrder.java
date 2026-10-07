package com.idlefish.trade.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 鉴定验货单（F-02）：买家下单时可选增值验货服务，送检至鉴定机构。
 * 金额单位：分。状态流转见 {@link com.idlefish.trade.inspection.InspectionStateMachine}。
 *
 * <p>不阻塞下单（REQ-01）：验货为可选增值，订单照常进入已支付流程，仅发货受冻结窗口约束（REQ-04）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_inspection_order")
public class InspectionOrder extends BaseEntity {

    private String inspectionNo;       // 验货单号（唯一，IV 前缀）
    private String orderNo;            // 关联订单号
    private Long itemId;               // 关联商品 ID
    private Long sellerId;             // 卖家
    private Long buyerId;              // 买家（送检方）
    private String type;               // 验货类型：STANDARD 标准 / ACCURATE 精密
    private String status;             // 状态机：WAIT_PICKUP/IN_TRANSIT/INSPECTING/PASSED/REJECTED/EXCEPTION/CANCELED
    private Long feeAmount;            // 服务费（分，REQ-10）
    private Long agencyId;             // 鉴定机构 ID
    private Long reportId;             // 关联报告 ID（机构回传后置入）
    private Long relatedDisputeId;     // 关联维权工单 ID（REQ-06 工单可调阅报告）
    private LocalDateTime timeoutAt;   // 超时时刻（REQ-09）：超过则归并 EXCEPTION
    private LocalDateTime receivedAt;  // 机构收件时间（IN_TRANSIT）
    private LocalDateTime startedAt;   // 验货开始时间（INSPECTING）
    private LocalDateTime finishedAt;  // 验货完成时间（终态）
    private String remark;             // 备注
}
