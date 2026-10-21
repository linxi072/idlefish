package com.idlefish.trade.pricing.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 估价请求（用户端）：选定品类与成色返回建议区间（REQ-02）。
 */
@Data
public class PriceEstimateRequest {
    /** 类目 ID。 */
    private Long categoryId;
    /** 成色等级 1~5（可空，默认 3）。 */
    private Integer conditionLevel;
}
