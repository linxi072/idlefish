package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 资金对账账单（ITER-T02 / F-11.4 深化，REQ-06/07/08）：日终多维对账结果持久化。
 * 每个 (reconcile_date, dimension) 唯一一笔，维度含 PAY / REFUND / SETTLE；
 * FREEZE 因提现无独立冻结终态（仅 pending/approved/rejected/done）而排除，避免误报。
 * 金额恒以「分」整型存储，避免浮点资损。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reconcile_bill")
public class ReconcileBill extends BaseEntity {

    private LocalDate reconcileDate;  // 对账自然日，与 dimension 组成唯一键
    private String dimension;         // PAY / REFUND / SETTLE
    private Integer expectedCount;    // 平台侧预期笔数
    private Integer actualCount;      // 资金流水侧实际笔数
    private Long expectedAmount;      // 预期金额合计（分）
    private Long actualAmount;        // 实际金额合计（分）
    private Long diffAmount;          // 金额差异（分，实际 - 预期）
    private Boolean matched;          // 笔数 + 金额 均一致
    private String status;            // PENDING / RESOLVED / CLOSED
    private Boolean alertSent;        // 是否已发送差异告警
    private String note;              // 备注 / 关闭说明
}
