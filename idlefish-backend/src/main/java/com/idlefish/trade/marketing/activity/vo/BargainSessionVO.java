package com.idlefish.trade.marketing.activity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 砍价会话 VO（用户端 / 分享页）。
 */
@Data
public class BargainSessionVO {

    private String sessionNo;
    private Long activityId;
    private Long initiatorUserId;
    private Long currentPriceFen;
    private Long floorPriceFen;
    private Long originPriceFen;
    private Integer targetAssistCount;
    private Integer assistCount;
    private String status;
    private LocalDateTime expireAt;
    private LocalDateTime qualifyExpireAt;
    /** 还差多少人成团（基于目标人数）。 */
    private Integer remainAssist;
    /** 已砍金额（分）= 原价 − 当前价。 */
    private Long cutTotalFen;
}
