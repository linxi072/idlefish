package com.idlefish.trade.admin.batch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 批量商品审核入参。 */
@Data
public class BatchAuditDTO {

    /** 目标商品 ID（1–200 条，上限由服务侧 BatchPolicy 强制）。 */
    @NotEmpty(message = "批量目标不能为空")
    private List<Long> ids;

    /** true 通过 / false 驳回。 */
    @NotNull(message = "审核结论不能为空")
    private Boolean pass;

    /** 驳回原因：驳回时必填（服务侧强校验，前端可留空由后端拦截）。 */
    private String reason;
}
