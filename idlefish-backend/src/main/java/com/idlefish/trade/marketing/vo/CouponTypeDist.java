package com.idlefish.trade.marketing.vo;

import lombok.Data;

/**
 * 券类型发放分布（F-13.5）：按券模板类型统计领取数。
 */
@Data
public class CouponTypeDist {

    /** 券类型：FULL_REDUCTION / NO_THRESHOLD / DISCOUNT。 */
    private String type;
    /** 该类型领取券数。 */
    private Long count;
}
