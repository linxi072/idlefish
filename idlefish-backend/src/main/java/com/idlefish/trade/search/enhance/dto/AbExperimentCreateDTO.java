package com.idlefish.trade.search.enhance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AB 实验创建入参（ITER-F04 / REQ-06）。
 */
@Data
public class AbExperimentCreateDTO {

    /** 实验名称（唯一，便于运营识别与冲突校验）。 */
    @NotBlank(message = "实验名称不可为空")
    @Size(max = 64, message = "实验名称长度不可超过 64")
    private String name;

    /**
     * 实验组流量占比（0~100）。建议 10~50，保证对照组保有基准流量。
     * 0 表示全量对照（常用于灰度前预热），100 表示全量实验。
     */
    @Min(value = 0, message = "实验组占比不可小于 0")
    @Max(value = 100, message = "实验组占比不可大于 100")
    private int treatmentPercent;

    /**
     * 策略参数（JSON 字符串，可选），如 {"recallBoost":1.2,"semanticRecall":true}。
     * 服务端会校验 JSON 合法性，非法将拒绝创建。
     */
    @Size(max = 4096, message = "策略参数长度不可超过 4096")
    private String strategyParams;

    /** 实验生效起始时间（可选；空表示立即生效）。 */
    private LocalDateTime startAt;

    /** 实验生效结束时间（可选；空表示长期生效）。 */
    private LocalDateTime endAt;

    /** 创建人标识（可选；缺省由控制器以当前管理员上下文填充）。 */
    @Size(max = 64, message = "创建人长度不可超过 64")
    private String creator;

    /** 实验说明（可选）。 */
    @Size(max = 255, message = "实验说明长度不可超过 255")
    private String description;
}
