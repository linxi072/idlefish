package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 提现申请（PRD §F4）：卖家将可提现余额提现到银行卡/微信零钱。
 * 状态：pending / approved / rejected / done。金额单位：分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_withdrawal")
public class Withdrawal extends BaseEntity {

    private Long userId;
    private Long amount;     // 提现金额（分）
    private String account;  // 提现账号（脱敏存储）
    private String status;   // pending / approved / rejected / done
    private LocalDateTime doneAt;
    private String transferNo;  // 渠道出款单号（微信 transfer_bill_no / out_bill_no）
    private String failReason;  // 出款失败原因（审批通过但真实出款失败时记录）
}
