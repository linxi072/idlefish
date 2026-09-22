package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 退款单（PRD §4.4）。状态见 {@link com.idlefish.trade.common.enums.RefundStatus}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_refund")
public class Refund extends BaseEntity {

    private String refundNo;    // 唯一
    private String orderNo;
    private String payNo;
    private Long buyerId;
    private Long sellerId;
    private Long itemId;
    private String type;        // only_refund / return_refund
    private Long amount;        // 分
    private String reason;
    private String status;      // RefundStatus.code
    private String logisticsNo;
    private LocalDateTime autoAgreeAt;  // 48h 自动同意
    private LocalDateTime platformAt;   // 5 天平台介入
    private LocalDateTime refundAt;
}
