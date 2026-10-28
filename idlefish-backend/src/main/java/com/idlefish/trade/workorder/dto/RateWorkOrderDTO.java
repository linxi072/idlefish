package com.idlefish.trade.workorder.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 用户关闭工单并满意度回访请求（F-12）。关闭即回填满意度，闭环服务体验。
 */
public class RateWorkOrderDTO {

    @NotNull(message = "满意度评分不能为空")
    @Min(value = 1, message = "满意度评分应为 1-5")
    @Max(value = 5, message = "满意度评分应为 1-5")
    private Integer satisfaction;

    public Integer getSatisfaction() {
        return satisfaction;
    }

    public void setSatisfaction(Integer satisfaction) {
        this.satisfaction = satisfaction;
    }
}
