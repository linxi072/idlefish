package com.idlefish.trade.trade.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 延时任务处理器集合（按 type 注册）。非 public 顶层类，由 Spring 组件扫描注册。
 */
@Service
class CloseOrderHandler implements DelayTaskHandler {
    private static final Logger log = LoggerFactory.getLogger(CloseOrderHandler.class);
    private final OrderService orderService;

    CloseOrderHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public String type() {
        return "ORDER_CLOSE";
    }

    @Override
    public void handle(String bizId, String payload) {
        orderService.closeExpiredSingle(bizId);
    }
}

@Service
class ConfirmReceiveHandler implements DelayTaskHandler {
    private final OrderService orderService;

    ConfirmReceiveHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public String type() {
        return "ORDER_CONFIRM";
    }

    @Override
    public void handle(String bizId, String payload) {
        orderService.confirmReceiveSingle(bizId);
    }
}

@Service
class RemindShipHandler implements DelayTaskHandler {
    private static final Logger log = LoggerFactory.getLogger(RemindShipHandler.class);

    @Override
    public String type() {
        return "REMIND_SHIP";
    }

    @Override
    public void handle(String bizId, String payload) {
        log.info("[remind] 订单 {} 已支付超时未发货，提醒卖家", bizId);
    }
}

@Service
class SettleHandler implements DelayTaskHandler {
    private final SettlementService settlementService;

    SettleHandler(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Override
    public String type() {
        return "SETTLE";
    }

    @Override
    public void handle(String bizId, String payload) {
        settlementService.processDue();
    }
}
