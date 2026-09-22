package com.idlefish.trade.trade.vo;

import lombok.Data;

/**
 * 退款单视图。
 */
@Data
public class RefundVO {

    private String refundNo;
    private String orderNo;
    private Long buyerId;
    private Long sellerId;
    private String type;        // only_refund / return_refund
    private Long amount;
    private String reason;
    private String status;      // RefundStatus.code
    private String logisticsNo;
    private String createdAt;
}
