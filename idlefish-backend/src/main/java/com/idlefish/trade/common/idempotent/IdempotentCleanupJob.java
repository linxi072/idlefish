package com.idlefish.trade.common.idempotent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 幂等记录清理任务：避免表无限增长。
 * 保留 7 天，兼顾对账与客诉排查需要；与项目既有调度（TradeScheduler/SearchScheduler）同风格。
 */
@Slf4j
@Component
public class IdempotentCleanupJob {

    private static final int RETAIN_DAYS = 7;

    private final IdempotentService idempotentService;

    public IdempotentCleanupJob(IdempotentService idempotentService) {
        this.idempotentService = idempotentService;
    }

    /** 每日凌晨 4:15 清理过期记录。 */
    @Scheduled(cron = "0 15 4 * * *")
    public void cleanup() {
        try {
            int n = idempotentService.cleanupExpired(RETAIN_DAYS);
            if (n > 0) {
                log.info("[idempotent] cleaned {} expired records (retain {} days)", n, RETAIN_DAYS);
            }
        } catch (Exception e) {
            // 清理失败不影响业务，仅记录
            log.warn("[idempotent] cleanup failed", e);
        }
    }
}
