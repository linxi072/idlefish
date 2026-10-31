package com.idlefish.trade.admin.batch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 批量封禁/解封入参（高风险，须二次确认）。 */
@Data
public class BatchBanDTO {

    @NotEmpty(message = "批量目标不能为空")
    private List<Long> ids;

    /** 目标状态：0 正常 / 1 封禁。 */
    @NotNull(message = "目标状态不能为空")
    private Integer status;

    /** 二次确认标记：高风险操作必须为 true，否则拒绝（REQ-03）。 */
    @NotNull(message = "二次确认标记不能为空")
    private Boolean confirmed;
}
