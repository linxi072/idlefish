package com.idlefish.trade.marketing.activity.vo;

import lombok.Data;

/**
 * 活动参与结果 VO（用户端）。
 */
@Data
public class ActivityJoinResultVO {

    private String recordNo;
    private Long activityId;
    private String kind;
    private String status;
    /** 成交金额 / 奖品价值（分）。 */
    private Long amountFen;
    /** 平台补贴（分）。 */
    private Long subsidyFen;
    /** 关联发放券（高价值/实物奖品以券形式发放）。 */
    private Long couponId;
    /** 砍价是否已达底价/目标。 */
    private Boolean qualified;
    /** 抽奖是否中奖。 */
    private Boolean won;
    /** 审计扩展（JSON）：抽奖概率快照 / 砍价逐刀明细（REQ-09）。 */
    private String extra;
}
