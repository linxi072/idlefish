package com.idlefish.trade.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 运营系数覆盖请求（运营后台）：在既有配置上调整全局系数并留痕（REQ-08）。
 */
@Data
public class PriceOverrideRequest {
    /** 类目 ID。 */
    @NotNull(message = "类目ID必填")
    private Long categoryId;
    /** 新的运营系数（须为正数）。 */
    @NotNull(message = "运营系数必填")
    @Positive(message = "运营系数须为正数")
    private BigDecimal multiplier;
    /** 覆盖原因（留痕）。 */
    @NotBlank(message = "覆盖原因必填")
    private String reason;
}
