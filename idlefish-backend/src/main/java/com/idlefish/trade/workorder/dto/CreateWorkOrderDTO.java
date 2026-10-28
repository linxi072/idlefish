package com.idlefish.trade.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建客服工单请求（F-12）。所有必填项加约束，控制器统一 {@code @Valid} 接线。
 */
public class CreateWorkOrderDTO {

    @NotBlank(message = "工单标题不能为空")
    private String subject;

    @NotBlank(message = "问题描述不能为空")
    private String description;

    @NotNull(message = "工单类型不能为空")
    private String type;

    @NotNull(message = "优先级不能为空")
    private String priority;

    @NotNull(message = "来源渠道不能为空")
    private String channel;

    /** 关联订单号（可选）。 */
    private String orderNo;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
}
