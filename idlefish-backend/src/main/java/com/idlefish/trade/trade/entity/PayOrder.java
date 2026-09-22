package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 支付单（资金托管）。状态见 {@link com.idlefish.trade.common.enums.PayStatus}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pay_order")
public class PayOrder extends BaseEntity {

    private String payNo;       // 唯一
    private String orderNo;
    private Long buyerId;
    private Long amount;        // 分
    private String channel;     // wechat
    private String status;      // PayStatus.code
    private String transactionId;
    private LocalDateTime paidAt;
}
