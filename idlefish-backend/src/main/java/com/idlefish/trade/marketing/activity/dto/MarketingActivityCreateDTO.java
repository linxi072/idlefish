package com.idlefish.trade.marketing.activity.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动创建 DTO（运营向导，REQ-14 在 service 层做亏损阻断校验）。
 * 玩法特有配置以 {@code playConfig}（JSON 字符串）透传，由对应玩法 service 解析与校验。
 */
@Data
public class MarketingActivityCreateDTO {

    private String kind;
    private String title;
    private Long itemId;
    private Long activityPrice;
    private Integer stock;
    private Integer limitPerUser;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String audienceType;
    private String audienceValue;
    private String riskLevel;
    private Long subsidyBudgetFen;
    private Long lossFloorFen;
    private String playConfig;
}
