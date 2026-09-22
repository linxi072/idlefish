package com.idlefish.trade.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 退款申请入参。
 */
public class RefundApplyDTO {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotBlank(message = "退款类型不能为空")
    private String type;        // only_refund / return_refund

    @NotNull(message = "退款金额不能为空")
    private Long amount;

    private String reason;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
