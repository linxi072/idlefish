package com.idlefish.trade.item.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 商品发布入参（PRD §4.2）。价格单位：分。
 */
public class ItemPublishDTO {

    @NotNull(message = "类目不能为空")
    private Long categoryId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 60, message = "标题最长 60 字")
    private String title;

    @NotBlank(message = "描述不能为空")
    @Size(max = 2000, message = "描述最长 2000 字")
    private String description;

    @NotNull(message = "售价不能为空")
    @Min(value = 1, message = "售价不能为 0")
    private Long price;

    @Min(value = 0, message = "原价不能为负")
    private Long originalPrice;

    @NotEmpty(message = "至少上传一张图片")
    @Size(max = 9, message = "最多 9 张图片")
    private List<String> images;

    private String videoUrl;

    @Min(1)
    @Max(5)
    private Integer conditionLevel;

    @Min(1)
    private Integer stock = 1;

    private String province;
    private String city;

    @Min(0)
    private Long freight = 0L;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    public Long getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(Long originalPrice) {
        this.originalPrice = originalPrice;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public Integer getConditionLevel() {
        return conditionLevel;
    }

    public void setConditionLevel(Integer conditionLevel) {
        this.conditionLevel = conditionLevel;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Long getFreight() {
        return freight;
    }

    public void setFreight(Long freight) {
        this.freight = freight;
    }
}
