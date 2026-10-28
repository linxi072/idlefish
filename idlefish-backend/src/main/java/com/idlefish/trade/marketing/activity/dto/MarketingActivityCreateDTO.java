package com.idlefish.trade.marketing.activity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动创建 DTO（运营向导，REQ-14 在 service 层做亏损阻断校验）。
 * 玩法特有配置以 {@code playConfig}（JSON 字符串）透传，由对应玩法 service 解析与校验。
 */
@Data
public class MarketingActivityCreateDTO {

    @NotBlank(message = "玩法类型必填")
    private String kind;
    @NotBlank(message = "活动标题必填")
    private String title;
    @NotNull(message = "商品ID必填")
    private Long itemId;
    @NotNull(message = "活动价必填")
    @Positive(message = "活动价须为正数")
    private Long activityPrice;
    private Integer stock;
    private Integer limitPerUser;
    @NotNull(message = "开始时间必填")
    private LocalDateTime startAt;
    @NotNull(message = "结束时间必填")
    private LocalDateTime endAt;
    private String audienceType;
    private String audienceValue;
    private String riskLevel;
    private Long subsidyBudgetFen;
    private Long lossFloorFen;
    private String playConfig;
}
