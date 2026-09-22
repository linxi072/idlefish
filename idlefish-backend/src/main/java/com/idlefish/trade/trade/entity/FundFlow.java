package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资金流水（买家支付 / 退款 / 结算入账）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_fund_flow")
public class FundFlow extends BaseEntity {

    private String bizNo;       // orderNo / refundNo / settleNo
    private Long userId;
    private String direction;   // IN / OUT
    private Long amount;        // 分
    private String type;        // PAY / REFUND / SETTLE / FREEZE
    private Long balanceAfter;
}
