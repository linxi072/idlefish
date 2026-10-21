package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资金对账差异明细（ITER-T02 / REQ-07）：账单级不平的逐笔下钻工单。
 * 记录某维度下某笔业务单据「平台侧 vs 资金流水侧」的金额/笔数差异，供人工核验。
 * 修复默认人工确认（REQ-06），防止自动修复引发二次资损。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reconcile_diff")
public class ReconcileDiff extends BaseEntity {

    private Long billId;            // 关联 t_reconcile_bill.id
    private String dimension;       // PAY / REFUND / SETTLE
    private String bizNo;           // 业务单号 orderNo / refundNo / settleNo
    private Long expectedAmount;    // 平台侧金额（分）
    private Long actualAmount;      // 资金流水侧金额（分）
    private Long diffAmount;        // 金额差异（分，实际 - 预期）
    private Integer expectedCount;  // 平台侧笔数
    private Integer actualCount;    // 资金流水侧笔数
    private String status;          // OPEN / RESOLVED / CLOSED
    private String handleNote;      // 处理说明
    private Long handlerId;         // 处理人（管理员 ID）
}
