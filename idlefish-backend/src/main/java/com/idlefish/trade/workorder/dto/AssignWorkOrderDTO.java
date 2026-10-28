package com.idlefish.trade.workorder.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 分配客服工单请求（F-12）。
 */
public class AssignWorkOrderDTO {

    @NotNull(message = "受理客服不能为空")
    private Long assigneeId;

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }
}
