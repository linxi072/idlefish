package com.idlefish.trade.marketing.activity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动对账 / 效果 VO（运营端，REQ-11、REQ-15）。
 */
@Data
public class ActivityReconcileVO {

    private Long activityId;
    private String kind;
    private String title;
    private Integer stockTotal;
    private Integer stockSold;
    private Integer stockRecycled;
    private Long subsidyTotalFen;
    private Integer participantCount;
    private Integer drawCount;
    private Integer winCount;
    /** 亏损额（分）。 */
    private Long lossFen;
    private String status;
    private LocalDateTime settledAt;
}
