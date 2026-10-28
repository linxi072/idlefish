package com.idlefish.trade.pricing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 估价配置保存请求（运营后台）：按品类配置基准价/系数/状态（REQ-08）。
 */
@Data
public class PriceConfigRequest {
    /** 类目 ID。 */
    @NotNull(message = "类目ID必填")
    private Long categoryId;
    /** 品类基准价（全新参考，分）。 */
    @NotNull(message = "基准价必填")
    @Positive(message = "基准价须为正数")
    private Long basePriceFen;
    /** 运营系数覆盖（默认 1.0）。 */
    @NotNull(message = "运营系数必填")
    @Positive(message = "运营系数须为正数")
    private BigDecimal multiplier;
    /** 状态：ENABLED / DISABLED（可空，默认 ENABLED）。 */
    private String status;
}
