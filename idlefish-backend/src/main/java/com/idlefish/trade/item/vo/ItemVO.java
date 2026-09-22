package com.idlefish.trade.item.vo;

import lombok.Data;

/**
 * 商品列表卡片 VO（买家侧检索 / 卖家侧管理列表）。
 */
@Data
public class ItemVO {

    private Long id;
    private Long sellerId;
    private String sellerName;
    private String sellerAvatar;
    private SellerVO seller;       // 卖家子对象（契约对齐 R-05）
    private Long categoryId;
    private String categoryName;
    private String title;
    private String cover;            // 封面 = images[0]
    private Long price;
    private Long originalPrice;
    private Integer conditionLevel;
    private String status;           // ItemStatus.code
    private String auditStatus;      // pending / pass / reject
    private String city;
    private Long freight;            // 0 包邮
    private Double priceYuan;        // 展示用：price/100，单位元（金额单位契约）
    private Double originalPriceYuan;
    private Double freightYuan;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favCount;
    private String createdAt;
}
