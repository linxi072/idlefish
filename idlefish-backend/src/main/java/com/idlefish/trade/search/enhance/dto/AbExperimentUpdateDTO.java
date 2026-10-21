package com.idlefish.trade.search.enhance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AB 实验更新入参（ITER-F04 / REQ-06/07）。
 * <p>全部字段可选：仅传入需变更字段，未传字段保持原值。回滚操作由专用端点触发，不在此 DTO 内。
 */
@Data
public class AbExperimentUpdateDTO {

    /** 实验组流量占比（0~100）。 */
    @Min(value = 0, message = "实验组占比不可小于 0")
    @Max(value = 100, message = "实验组占比不可大于 100")
    private Integer treatmentPercent;

    /** 策略参数（JSON 字符串，可选）。 */
    @Size(max = 4096, message = "策略参数长度不可超过 4096")
    private String strategyParams;

    /** 实验生效起始时间（可选）。 */
    private LocalDateTime startAt;

    /** 实验生效结束时间（可选）。 */
    private LocalDateTime endAt;

    /** 实验说明（可选）。 */
    @Size(max = 255, message = "实验说明长度不可超过 255")
    private String description;
}
