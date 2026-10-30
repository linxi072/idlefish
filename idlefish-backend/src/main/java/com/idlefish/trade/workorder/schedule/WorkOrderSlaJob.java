package com.idlefish.trade.workorder.schedule;

import com.idlefish.trade.workorder.service.WorkOrderSlaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 客服工单时效 SLA 调度（F-12 补完）：定时扫描超时工单并标记 + 触达。
 * <p>与 {@code GrowthDecayJob} / {@code TradeScheduler} 同范式：cron + try/catch + 日志，
 * 单任务失败绝不影响其他调度与业务主流程。</p>
 */
@Slf4j
@Component
public class WorkOrderSlaJob {

    private final WorkOrderSlaService workOrderSlaService;

    public WorkOrderSlaJob(WorkOrderSlaService workOrderSlaService) {
        this.workOrderSlaService = workOrderSlaService;
    }

    /** 每 30 分钟扫描一次未结工单的时效超时情况（首响或解决超期即标记并触达）。 */
    @Scheduled(cron = "0 */30 * * * *")
    public void scanBreached() {
        try {
            int n = workOrderSlaService.scanBreached(WorkOrderSlaService.DEFAULT_SCAN_LIMIT);
            if (n > 0) {
                log.info("[workorder] sla breach scan: marked={}", n);
            }
        } catch (Exception e) {
            log.warn("[workorder] sla breach scan failed", e);
        }
    }
}
