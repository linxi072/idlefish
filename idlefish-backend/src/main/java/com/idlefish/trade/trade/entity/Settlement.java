package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 结算单（T+1 放款给卖家）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_settlement")
public class Settlement extends BaseEntity {

    private String settleNo;    // 唯一
    private Long sellerId;
    private String orderNo;
    private Long amount;        // 卖家应收（分）
    private Long platformFee;   // 平台佣金（分）
    private String status;      // pending / settled / frozen
    private LocalDateTime settleAt; // 计划结算时间（T+1）
}
