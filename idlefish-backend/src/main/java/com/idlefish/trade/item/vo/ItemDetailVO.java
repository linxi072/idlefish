package com.idlefish.trade.item.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 商品详情 VO（基于 {@link ItemVO} 补充富字段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ItemDetailVO extends ItemVO {

    private String description;
    private List<String> images;
    private String videoUrl;
    private Integer stock;
}
