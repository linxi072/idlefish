package com.idlefish.trade.item.dto;

/**
 * 商品列表查询入参（买家侧检索 / 卖家侧管理）。
 */
public class ItemQueryDTO {

    private Long categoryId;
    private String keyword;
    private String status;          // 卖家查询自身商品时按状态过滤
    private Integer conditionLevel;
    private Long minPrice;
    private Long maxPrice;
    private String sort = "new";    // new / price_asc / price_desc / hot
    private Integer page = 1;
    private Integer size = 20;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getConditionLevel() {
        return conditionLevel;
    }

    public void setConditionLevel(Integer conditionLevel) {
        this.conditionLevel = conditionLevel;
    }

    public Long getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(Long minPrice) {
        this.minPrice = minPrice;
    }

    public Long getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(Long maxPrice) {
        this.maxPrice = maxPrice;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
