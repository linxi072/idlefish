package com.idlefish.trade.marketing.activity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动展示 VO（用户端 / 运营端共用）。
 * 玩法特有配置以 {@code playConfig} 原样透传（JSON），前端按 {@code kind} 解析，避免后端耦合展示逻辑。
 */
@Data
public class MarketingActivityVO {

    private Long id;
    private String kind;
    private String title;
    private Long itemId;
    private Long activityPrice;
    private Integer stock;
    private Integer soldCount;
    private Integer limitPerUser;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String audienceType;
    private String riskLevel;
    private Long subsidyBudgetFen;
    private Long subsidyUsedFen;
    /** 玩法配置原样透传（JSON）。 */
    private String playConfig;
    /** 当前用户是否已参与（用于按钮置灰态）。 */
    private Boolean joined;
}
