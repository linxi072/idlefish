package com.idlefish.trade.trade.vo;

import lombok.Data;

/**
 * 订单内嵌的商品轻量视图（R-12：补齐订单详情 seller/商品契约，真实后端模式下不再为空）。
 * 仅携带下单页 / 订单详情所需的最小字段，避免与完整 ItemVO 耦合。
 */
@Data
public class OrderItemVO {

    private Long id;
    private String title;
    private String cover;
    private Long sellerId;
    /** 嵌套 seller 引用，对齐前端 o.item.seller.id */
    private SellerRef seller;

    @Data
    public static class SellerRef {
        private Long id;
    }
}
