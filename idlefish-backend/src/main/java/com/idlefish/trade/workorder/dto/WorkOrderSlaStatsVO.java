package com.idlefish.trade.workorder.dto;

import java.time.LocalDateTime;

/**
 * 客服工单时效 SLA 统计（F-12 补完）。
 *
 * <p>{@code complianceRate} 为综合达标率（0~1）＝（总数 − 超时数）/ 总数，无样本默认 1.0。
 */
public record WorkOrderSlaStatsVO(int days,
                                  long total,
                                  long breached,
                                  long resolved,
                                  long openBreached,
                                  double complianceRate,
                                  LocalDateTime generatedAt) {

    public static WorkOrderSlaStatsVO empty(int days) {
        return new WorkOrderSlaStatsVO(days, 0, 0, 0, 0, 1.0, LocalDateTime.now());
    }
}
