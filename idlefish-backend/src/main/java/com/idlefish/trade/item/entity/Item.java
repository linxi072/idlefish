package com.idlefish.trade.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品（PRD §4.2）。金额单位：分；状态见 {@link com.idlefish.trade.common.enums.ItemStatus}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_item")
public class Item extends BaseEntity {

    private Long sellerId;
    private Long categoryId;
    private String title;
    private String description;
    private Long price;          // 售价（分）
    private Long originalPrice;  // 原价（分）
    private String images;       // JSON 字符串，图片 URL 数组
    private String videoUrl;
    private Integer conditionLevel; // 1 全新 ~ 5 功能完好
    private Integer stock;
    private String status;       // ItemStatus.code
    private String auditStatus;  // pending / pass / reject
    private String auditReason;
    private String province;
    private String city;
    private Long freight;        // 运费（分），0 表示包邮
    private Integer viewCount;
    private Integer likeCount;
    private Integer favCount;

    @Version
    private Integer version;     // 乐观锁，防超卖
}
