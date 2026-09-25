package com.idlefish.trade.trade.vo;

import lombok.Data;

/**
 * 下单结果（R-14 契约对齐）：返回订单号与应付金额（分），供前端跳转与展示。
 */
@Data
public class OrderCreateVO {

    private String orderNo;
    /** 应付金额（分），金额单位契约：前端展示需 /100 转元 */
    private Long amount;
    /** 优惠券抵扣金额（分），无券为 0 */
    private Long discountAmount;
}
