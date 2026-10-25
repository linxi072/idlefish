package com.idlefish.trade.member.schedule;

import com.idlefish.trade.member.service.BenefitExchangeService;
import com.idlefish.trade.member.service.GrowthDecayService;
import com.idlefish.trade.member.service.GrowthDecayService.DecaySummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 成长值衰减调度（ITER-F07 / REQ-05 + REQ-12 权益过期回收）。
 * <p>与 {@code IdempotentCleanupJob} / {@code TradeScheduler} 同范式：cron + try/catch + 日志，
 * 单任务失败绝不影响其他调度与业务主流程。</p>
 * <ul>
 *   <li>03:30 衰减扫描：凌晨低峰执行，批量衰减 + 降级重算；</li>
 *   <li>04:20 权益过期回收：把越过下架时间仍为上架的权益统一下架（REQ-12）；</li>
 *   <li>09:00 衰减预警：早间发送「即将衰减」提醒，给用户留出全天的活跃窗口。</li>
 * </ul>
 */
@Slf4j
@Component
public class GrowthDecayJob {

    private final GrowthDecayService growthDecayService;
    private final BenefitExchangeService benefitExchangeService;

    public GrowthDecayJob(GrowthDecayService growthDecayService,
                          BenefitExchangeService benefitExchangeService) {
        this.growthDecayService = growthDecayService;
        this.benefitExchangeService = benefitExchangeService;
    }

    /** 每日 03:30 扫描不活跃账户并按规则衰减成长值（低于阈值自动降级）。 */
    @Scheduled(cron = "0 30 3 * * *")
    public void scanAndDecay() {
        try {
            DecaySummary s = growthDecayService.scanAndDecay();
            if (s.scanned() > 0) {
                log.info("[growth] decay scan: scanned={}, decayed={}, downgraded={}",
                        s.scanned(), s.decayed(), s.downgraded());
            }
        } catch (Exception e) {
            log.warn("[growth] decay scan failed", e);
        }
    }

    /** 每日 04:20 回收已过期权益（REQ-12）：仅改上架状态，不影响历史兑换记录。 */
    @Scheduled(cron = "0 20 4 * * *")
    public void recoverExpiredBenefits() {
        try {
            int n = benefitExchangeService.recoverExpired();
            if (n > 0) {
                log.info("[growth] recovered {} expired benefits", n);
            }
        } catch (Exception e) {
            log.warn("[growth] benefit expire recovery failed", e);
        }
    }

    /** 每日 09:00 发送「即将衰减」提前通知（REQ-05 提前 7 日，当日去重）。 */
    @Scheduled(cron = "0 0 9 * * *")
    public void notifyUpcomingDecay() {
        try {
            DecaySummary s = growthDecayService.notifyUpcomingDecay();
            if (s.scanned() > 0) {
                log.info("[growth] decay notify: scanned={}, notified={}",
                        s.scanned(), s.notified());
            }
        } catch (Exception e) {
            log.warn("[growth] decay notify failed", e);
        }
    }
}
