package com.idlefish.trade.favorite.vo;

import lombok.Data;

/**
 * 收藏卡片 VO（含被收藏商品的标题/封面/价格）。
 */
@Data
public class FavoriteVO {

    private Long id;
    private Long itemId;
    private String title;
    private String cover;            // 封面 = images[0]
    private Long price;              // 分
    private Double priceYuan;        // 展示用：price/100，单位元（金额单位契约）
    private String createdAt;
}
