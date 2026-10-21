package com.idlefish.trade.trade.vo;

import com.idlefish.trade.trade.entity.ReconcileBill;
import com.idlefish.trade.trade.entity.ReconcileDiff;
import lombok.Data;

import java.util.List;

/**
 * 对账账单详情视图（ITER-T02）：账单头 + 差异明细列表，供后台下钻核验。
 */
@Data
public class ReconcileBillDetailVO {

    /** 账单头（REQ-06 汇总结果）。 */
    private ReconcileBill bill;

    /** 差异明细（REQ-07 逐笔下钻），matched=true 时为空列表。 */
    private List<ReconcileDiff> diffs;
}
