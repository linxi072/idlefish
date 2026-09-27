package com.idlefish.trade.analytics.vo;

import lombok.Data;

/**
 * 品类 GMV 分布项（F-15.5）。金额单位：分。
 */
@Data
public class AnalyticsCategoryGmv {

    private Long categoryId;
    private String categoryName;
    private Long orderCount;
    private Long gmv;
}
