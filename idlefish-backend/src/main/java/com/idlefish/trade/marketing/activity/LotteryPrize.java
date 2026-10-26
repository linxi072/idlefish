package com.idlefish.trade.marketing.activity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 抽奖奖品（ITER-F08 / REQ-09、REQ-10）。
 * 概率配置留痕、中奖可追溯；实物/高价值奖品需补充实名与收货信息（REQ-10）。
 */
@Data
public class LotteryPrize implements Serializable {

    /** 奖品 ID（活动内唯一）。 */
    private String prizeId;

    /** 奖品名称。 */
    private String name;

    /** 奖品类型：COUPON（券）/ PHYSICAL（实物）/ VIRTUAL（虚拟）。 */
    private String type;

    /** 奖品价值（分），用于 ROI 核算与高价值判定。 */
    private Long valueFen;

    /** 配置概率（0~1，所有奖品之和 <=1，余量为「未中奖」）。 */
    private BigDecimal probability;

    /** 奖品库存（份数）。 */
    private int stock;

    /** 是否需补充实名与收货信息（实物/高价值，REQ-10）。 */
    private boolean realNameRequired;

    /** 关联券模板（type=COUPON 时由 F-10 发放，REQ-10）。 */
    private Long couponTemplateId;
}
