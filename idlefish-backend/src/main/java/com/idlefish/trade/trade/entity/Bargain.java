package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 议价卡片（PRD §D3）：围绕商品会话的结构化议价。
 * 24h 有效期，卖家接受后同步成交价。金额单位：分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_bargain")
public class Bargain extends BaseEntity {

    private String convId;       // 会话 ID
    private Long itemId;         // 商品 ID
    private Long buyerId;        // 出价方（买家）
    private Long sellerId;       // 接受方（卖家）
    private Long originPrice;    // 原价（分）
    private Long offerPrice;     // 出价（分）
    private String status;       // pending / accepted / rejected / expired
    private LocalDateTime expireAt;
}
