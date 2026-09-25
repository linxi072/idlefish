package com.idlefish.trade.trade.vo;

import lombok.Data;

/**
 * 订单视图（聚合商品快照、支付状态、地址快照）。
 */
@Data
public class OrderVO {

    private String orderNo;
    private Long buyerId;
    private Long sellerId;
    private Long itemId;
    private String title;
    private String cover;
    /** R-12：订单内嵌商品轻量视图，真实后端模式下供前端读取 item.id / item.seller.id */
    private OrderItemVO item;
    private Long unitPrice;
    private Integer quantity;
    private Long totalAmount;
    private Long freight;
    private Long payAmount;
    private Long amount;            // 兼容前端：应付总额 = payAmount（单位：分）
    private Long discountAmount;    // 优惠券抵扣（分）
    private Long couponId;          // 使用的用户券 ID
    private Double unitPriceYuan;   // 展示用：对应字段/100，单位元（金额单位契约）
    private Double totalAmountYuan;
    private Double freightYuan;
    private Double payAmountYuan;
    private Double amountYuan;
    private Double discountAmountYuan;
    private String status;          // OrderStatus.code
    private String remark;
    private String payStatus;       // PayOrder.status
    private String addressReceiver;
    private String addressPhone;
    private String addressDetail;
    private String logisticsNo;
    private String createdAt;
}
