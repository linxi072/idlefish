package com.idlefish.trade.search.serving.dto;

/**
 * 检索入参传输对象（ITER-T04）：搜推端口与远程 HTTP 调用共用的稳定结构，
 * 字段与买家侧 {@code ItemQueryDTO} 对齐（仅保留检索相关维度，金额单位为分）。
 */
public class ItemQuery {

    private String keyword;
    private Long categoryId;
    private String city;
    private String province;
    private Long minPrice;
    private Long maxPrice;
    private Integer conditionLevel;
    private Boolean localOnly;
    private String sort = "new";
    private Integer page = 1;
    private Integer size = 20;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
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

    public Integer getConditionLevel() {
        return conditionLevel;
    }

    public void setConditionLevel(Integer conditionLevel) {
        this.conditionLevel = conditionLevel;
    }

    public Boolean getLocalOnly() {
        return localOnly;
    }

    public void setLocalOnly(Boolean localOnly) {
        this.localOnly = localOnly;
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

    /** 由买家侧 ItemQueryDTO 转换（保持调用方无感）。 */
    public static ItemQuery from(com.idlefish.trade.item.dto.ItemQueryDTO q) {
        ItemQuery iq = new ItemQuery();
        if (q == null) {
            return iq;
        }
        iq.keyword = q.getKeyword();
        iq.categoryId = q.getCategoryId();
        iq.city = q.getCity();
        iq.province = q.getProvince();
        iq.minPrice = q.getMinPrice();
        iq.maxPrice = q.getMaxPrice();
        iq.conditionLevel = q.getConditionLevel();
        iq.localOnly = q.getLocalOnly();
        iq.sort = q.getSort();
        iq.page = q.getPage();
        iq.size = q.getSize();
        return iq;
    }

    /** 转回买家侧 ItemQueryDTO（内联适配器复用既有检索管线）。 */
    public com.idlefish.trade.item.dto.ItemQueryDTO toQueryDTO() {
        com.idlefish.trade.item.dto.ItemQueryDTO q = new com.idlefish.trade.item.dto.ItemQueryDTO();
        q.setKeyword(keyword);
        q.setCategoryId(categoryId);
        q.setCity(city);
        q.setProvince(province);
        q.setMinPrice(minPrice);
        q.setMaxPrice(maxPrice);
        q.setConditionLevel(conditionLevel);
        q.setLocalOnly(localOnly);
        q.setSort(sort);
        q.setPage(page);
        q.setSize(size);
        return q;
    }
}
