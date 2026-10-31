package com.idlefish.trade.compliance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 偏好开关入参（营销通知 / 关怀模式）。 */
@Data
public class PreferenceDTO {

    @NotNull(message = "开关值不能为空")
    private Boolean enabled;
}
