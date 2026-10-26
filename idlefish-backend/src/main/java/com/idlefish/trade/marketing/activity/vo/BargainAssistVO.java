package com.idlefish.trade.marketing.activity.vo;

import lombok.Data;

/**
 * 砍价助力结果 VO（REQ-08）。
 */
@Data
public class BargainAssistVO {

    private String sessionNo;
    /** 本次助力砍价额（分）。 */
    private Long cutFen;
    /** 砍后当前价（分）。 */
    private Long currentPriceFen;
    /** 是否已达成底价或目标人数（生成下单资格）。 */
    private Boolean qualified;
    private String status;
}
