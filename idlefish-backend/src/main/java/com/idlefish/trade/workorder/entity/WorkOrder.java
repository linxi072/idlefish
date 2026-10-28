package com.idlefish.trade.workorder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 客服工单（F-12）：用户提交问题 → 客服分配 → 处理/待用户补充 → 解决 → 关闭（含满意度回访）。
 *
 * <p>与维权纠纷（{@code t_dispute}）解耦：本表是「通用客服流转」载体，不承载资金裁决。
 * 状态流转见 {@link com.idlefish.trade.workorder.WorkOrderStateMachine}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_work_order")
public class WorkOrder extends BaseEntity {

    private String ticketNo;        // 工单号（唯一，WO 前缀）
    private Long userId;            // 发起用户 ID
    private String type;            // 工单类型：GENERAL/REFUND/TECHNICAL/COMPLAINT/SUGGESTION
    private String priority;        // 优先级：LOW/NORMAL/HIGH/URGENT
    private String status;          // OPEN/IN_PROGRESS/WAITING_USER/RESOLVED/CLOSED
    private String channel;         // 来源渠道：APP/WEB/MINI/PHONE
    private String subject;         // 工单标题
    private String description;     // 问题描述
    private String orderNo;         // 关联订单号（可选）
    private Long assigneeId;        // 受理客服（admin 用户 ID，分配前为空）
    private String resolution;      // 解决方案/处理说明
    private Integer satisfaction;   // 满意度评分 1-5（用户关闭时回填）
    private LocalDateTime firstResponseAt; // 首次响应时间（SLA 度量）
    private LocalDateTime resolvedAt;      // 解决时间
    private LocalDateTime closedAt;       // 关闭时间
}
