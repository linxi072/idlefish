package com.idlefish.trade.marketing.activity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 抽奖奖品（ITER-F08 / REQ-09、REQ-10）。
 * <p>
 * 与统一活动一对一：{@code probability} 为配置概率（所有奖品之和 ≤ 1，余量为「未中奖」），
 * {@code stock} 为奖品份数，原子扣减防超发（REQ-06）。{@code coupon_template_id} 指向 F-10 券模板。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lottery_prize")
public class LotteryPrizeEntity extends BaseEntity {

    private Long activityId;

    /** 活动内奖品唯一标识。 */
    private String prizeId;

    private String name;

    /** 奖品类型：COUPON / PHYSICAL / VIRTUAL。 */
    private String type;

    /** 奖品价值（分），用于 ROI 核算与高价值判定。 */
    private Long valueFen;

    /** 配置概率（0~1）。 */
    private BigDecimal probability;

    /** 奖品份数（原子扣减）。 */
    private Integer stock;

    /** 关联券模板（type=COUPON，REQ-10 经 F-10 发放）。 */
    private Long couponTemplateId;

    /** 奖品状态：ACTIVE / INACTIVE。 */
    private String status;
}
