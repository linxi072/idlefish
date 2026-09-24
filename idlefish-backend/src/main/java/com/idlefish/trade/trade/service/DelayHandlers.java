package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.OrderMapper;
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
    private final NotificationService notificationService;
    private final OrderMapper orderMapper;

    RemindShipHandler(NotificationService notificationService, OrderMapper orderMapper) {
        this.notificationService = notificationService;
        this.orderMapper = orderMapper;
    }

    @Override
    public String type() {
        return "REMIND_SHIP";
    }

    @Override
    public void handle(String bizId, String payload) {
        log.info("[remind] 订单 {} 已支付超时未发货，提醒卖家", bizId);
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, bizId));
        if (order != null) {
            // F-02 通知中心：发货提醒触达卖家（best-effort）
            notificationService.notify(order.getSellerId(), NotificationType.REMIND_SHIP, bizId, "发货提醒",
                    "订单 " + bizId + " 已支付超过 72 小时未发货，请尽快发货");
        }
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
