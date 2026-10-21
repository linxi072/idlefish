package com.idlefish.trade.pricing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 品类估价配置（ITER-F03）：运营可配置的品类基准价与全局系数覆盖（REQ-08）。
 * basePriceFen 为「全新」参考基准（分）；multiplier 为运营系数覆盖（默认 1.0，留痕由审计表承载）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_price_estimate_config")
public class PriceEstimateConfig extends BaseEntity {

    /** 类目 ID（唯一）。 */
    private Long categoryId;
    /** 品类基准价（全新参考，分，REQ-01）。 */
    private Long basePriceFen;
    /** 运营系数覆盖（默认 1.0，作用到规则兜底与 U01 规则估价）。 */
    private BigDecimal multiplier;
    /** 最近操作人（管理员 ID）。 */
    private Long operatorId;
    /** 状态：ENABLED / DISABLED。 */
    private String status;
}
