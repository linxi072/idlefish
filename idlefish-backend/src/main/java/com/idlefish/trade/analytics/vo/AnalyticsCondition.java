package com.idlefish.trade.analytics.vo;

import lombok.Data;

/**
 * 商品成色分布项（F-15.5）。conditionLevel：1 全新 ~ 5 功能完好。
 */
@Data
public class AnalyticsCondition {

    private Integer conditionLevel;
    private Long count;
}
