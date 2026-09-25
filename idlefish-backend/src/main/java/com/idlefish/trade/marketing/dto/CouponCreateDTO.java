package com.idlefish.trade.marketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 发券入参（PC 运营后台）。
 */
public class CouponCreateDTO {

    @NotBlank(message = "券名称不能为空")
    private String name;

    @NotBlank(message = "券类型不能为空")
    private String type;                 // FULL_REDUCTION / NO_THRESHOLD / DISCOUNT

    private Long thresholdAmount = 0L;   // 满减门槛（分）
    private Long reduceAmount = 0L;      // 减免金额（分）
    private Double discountRate = 1.0;   // 折扣率（DISCOUNT）
    private Long maxDiscountAmount = 0L; // 折扣封顶（分）

    @NotBlank(message = "适用范围不能为空")
    private String scope = "ALL";        // ALL / CATEGORY / ITEM

    private Long scopeId;                // 适用类目/商品 ID

    @NotNull(message = "发放总量不能为空")
    private Integer totalCount = 0;

    private Integer perUserLimit = 1;

    @NotNull(message = "生效时间不能为空")
    private LocalDateTime startAt;

    @NotNull(message = "失效时间不能为空")
    private LocalDateTime endAt;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getThresholdAmount() { return thresholdAmount; }
    public void setThresholdAmount(Long thresholdAmount) { this.thresholdAmount = thresholdAmount; }
    public Long getReduceAmount() { return reduceAmount; }
    public void setReduceAmount(Long reduceAmount) { this.reduceAmount = reduceAmount; }
    public Double getDiscountRate() { return discountRate; }
    public void setDiscountRate(Double discountRate) { this.discountRate = discountRate; }
    public Long getMaxDiscountAmount() { return maxDiscountAmount; }
    public void setMaxDiscountAmount(Long maxDiscountAmount) { this.maxDiscountAmount = maxDiscountAmount; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getScopeId() { return scopeId; }
    public void setScopeId(Long scopeId) { this.scopeId = scopeId; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getPerUserLimit() { return perUserLimit; }
    public void setPerUserLimit(Integer perUserLimit) { this.perUserLimit = perUserLimit; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
}
