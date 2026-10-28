package com.idlefish.trade.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建活动入参（F-13.3）。金额单位：分。
 */
public class ActivityCreateDTO implements Serializable {

    @NotNull(message = "商品ID必填")
    private Long itemId;
    @NotBlank(message = "活动类型必填")
    private String type;          // SECKILL / GROUP
    @NotNull(message = "活动价必填")
    @Positive(message = "活动价须为正数")
    private Long activityPrice;   // 活动价（分）
    @NotNull(message = "活动库存必填")
    @Min(value = 0, message = "活动库存不能为负")
    private Integer stock;        // 活动库存
    private Integer limitPerUser; // 每人限购（<=0 不限）
    private Integer groupSize;    // 拼团成团人数
    private Integer groupValidMinutes; // 拼团有效分钟
    @NotNull(message = "开始时间必填")
    private LocalDateTime startAt;
    @NotNull(message = "结束时间必填")
    private LocalDateTime endAt;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getActivityPrice() {
        return activityPrice;
    }

    public void setActivityPrice(Long activityPrice) {
        this.activityPrice = activityPrice;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getLimitPerUser() {
        return limitPerUser;
    }

    public void setLimitPerUser(Integer limitPerUser) {
        this.limitPerUser = limitPerUser;
    }

    public Integer getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(Integer groupSize) {
        this.groupSize = groupSize;
    }

    public Integer getGroupValidMinutes() {
        return groupValidMinutes;
    }

    public void setGroupValidMinutes(Integer groupValidMinutes) {
        this.groupValidMinutes = groupValidMinutes;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }
}
