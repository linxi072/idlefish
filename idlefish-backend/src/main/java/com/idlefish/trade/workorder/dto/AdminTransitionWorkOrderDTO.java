package com.idlefish.trade.workorder.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员流转客服工单请求（F-12）。目标状态必填；流转到 RESOLVED 时 {@code resolution} 由服务强校验必填。
 */
public class AdminTransitionWorkOrderDTO {

    @NotBlank(message = "目标状态不能为空")
    private String toStatus;

    /** 解决方案/处理说明（流转到 RESOLVED 时必填）。 */
    private String resolution;

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }
}
