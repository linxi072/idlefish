package com.idlefish.trade.pricing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 偏离判定请求（用户端）：给定报价，判定其相对建议区间的偏离（REQ-09）。
 */
@Data
public class PriceDeviationRequest {
    /** 类目 ID。 */
    @NotNull(message = "类目ID必填")
    private Long categoryId;
    /** 成色等级 1~5（可空，默认 3）。 */
    private Integer conditionLevel;
    /** 待校验报价（分，REQ-01）。 */
    @NotNull(message = "报价必填")
    @Positive(message = "报价须为正数")
    private Long price;
}
