package com.idlefish.trade.trade.schedule;

import com.idlefish.trade.trade.service.OrderService;
import com.idlefish.trade.trade.service.RefundService;
import com.idlefish.trade.trade.service.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 交易域定时任务（PRD §4.4 超时与结算规则）：
 * - 30 分钟未支付自动关单并释放库存
 * - 已支付 72h 未发货提醒卖家
 * - 退款单 48h 卖家未处理自动同意（见 RefundService.autoAgree）
 * - 退款单 5 天未解决平台介入（见 RefundService.autoPlatform）
 * - 运输中 10 天未确认收货自动确认
 * - T+1 结算到期放款
 * <p>
 * 演示环境以较短周期运行，便于验证；生产可改为分布式调度（XXL-JOB / Scheduled 集群防重）。
 */
@Component
public class TradeScheduler {

    private static final Logger log = LoggerFactory.getLogger(TradeScheduler.class);

    private final OrderService orderService;
    private final RefundService refundService;
    private final SettlementService settlementService;

    public TradeScheduler(OrderService orderService, RefundService refundService, SettlementService settlementService) {
        this.orderService = orderService;
        this.refundService = refundService;
        this.settlementService = settlementService;
    }

    /** 每 60 秒扫描：关闭超时未支付订单。 */
    @Scheduled(fixedDelay = 60_000)
    public void closeExpired() {
        try {
            orderService.closeExpiredOrders();
        } catch (Exception e) {
            log.error("[scheduler] closeExpired failed", e);
        }
    }

    /** 每 5 分钟：提醒超 72h 未发货。 */
    @Scheduled(fixedDelay = 300_000)
    public void remind() {
        try {
            long n = orderService.remindUnshipped();
            if (n > 0) {
                log.info("[scheduler] 提醒 {} 笔待发货订单", n);
            }
        } catch (Exception e) {
            log.error("[scheduler] remind failed", e);
        }
    }

    /** 每 5 分钟：退款单 48h 自动同意 + 5 天平台介入。 */
    @Scheduled(fixedDelay = 300_000)
    public void refundAuto() {
        try {
            refundService.autoAgree();
            refundService.autoPlatform();
        } catch (Exception e) {
            log.error("[scheduler] refundAuto failed", e);
        }
    }

    /** 每 10 分钟：运输中 10 天自动确认收货。 */
    @Scheduled(fixedDelay = 600_000)
    public void autoConfirm() {
        try {
            orderService.autoConfirmReceive();
        } catch (Exception e) {
            log.error("[scheduler] autoConfirm failed", e);
        }
    }

    /** 每 2 分钟：T+1 结算到期放款。 */
    @Scheduled(fixedDelay = 120_000)
    public void settleDue() {
        try {
            settlementService.processDue();
        } catch (Exception e) {
            log.error("[scheduler] settleDue failed", e);
        }
    }
}
