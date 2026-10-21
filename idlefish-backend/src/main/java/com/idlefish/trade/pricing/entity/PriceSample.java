package com.idlefish.trade.pricing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 历史成交样本（ITER-F03）：成交价回流，用于估价校准（REQ-05）。
 * 金额单位：分（dealPriceFen）。按 (品类, 成色) 维度聚合分位估价。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_price_sample")
public class PriceSample extends BaseEntity {

    /** 关联商品 ID。 */
    private Long itemId;
    /** 类目 ID。 */
    private Long categoryId;
    /** 成色等级 1~5（1 全新 ~ 5 功能完好）。 */
    private Integer conditionLevel;
    /** 成交价（分，REQ-01）。 */
    private Long dealPriceFen;
    /** 成交时间。 */
    private LocalDateTime dealAt;
    /** 样本来源：deal(真实成交) / manual(人工录入)。 */
    private String source;
}
