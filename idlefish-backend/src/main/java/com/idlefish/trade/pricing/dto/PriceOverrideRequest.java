package com.idlefish.trade.pricing.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 运营系数覆盖请求（运营后台）：在既有配置上调整全局系数并留痕（REQ-08）。
 */
@Data
public class PriceOverrideRequest {
    /** 类目 ID。 */
    private Long categoryId;
    /** 新的运营系数（须为正数）。 */
    private BigDecimal multiplier;
    /** 覆盖原因（留痕）。 */
    private String reason;
}
