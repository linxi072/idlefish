package com.idlefish.trade.marketing.activity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 活动对账流水（ITER-F08 / REQ-01、REQ-11）：每活动一条，活动结束/异常时结算。
 * 记录库存消耗、补贴成本与亏损（成本−营收），供财务核算与 ROI 归因（REQ-15）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_activity_reconciliation")
public class ActivityReconciliation extends BaseEntity {

    private Long activityId;

    /** 玩法类型。 */
    private String kind;

    /** 库存总量。 */
    private Integer stockTotal;

    /** 实际售出/消耗。 */
    private Integer stockSold;

    /** 回收未使用库存（REQ-11）。 */
    private Integer stockRecycled;

    /** 补贴总成本（分）。 */
    private Long subsidyTotalFen;

    /** 参与人次。 */
    private Integer participantCount;

    /** 抽奖次数。 */
    private Integer drawCount;

    /** 中奖次数。 */
    private Integer winCount;

    /** 亏损额（分）= 成本−营收，>=0 表示亏。 */
    private Long lossFen;

    /** 对账状态：OPEN / SETTLED。 */
    private String status;

    private LocalDateTime settledAt;
}
