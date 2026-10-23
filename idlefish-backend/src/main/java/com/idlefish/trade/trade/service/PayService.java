package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.TraceContext;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.enums.PayStatus;
import com.idlefish.trade.trade.entity.FundFlowBuilder;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.trade.service.DelayQueueService;
import com.idlefish.trade.marketing.service.PointService;
import com.idlefish.trade.marketing.invite.service.InviteService;
import com.idlefish.trade.member.service.MemberLevelService;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.pricing.service.PricingService;
import com.idlefish.trade.notify.service.NotificationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 支付服务：预下单、支付结果通知（幂等）、退款。
 */
@Service
public class PayService {

    private final PayOrderMapper payOrderMapper;
    private final OrderMapper orderMapper;
    private final FundFlowMapper fundFlowMapper;
    private final FundEscrowService escrow;
    private final ObjectMapper objectMapper;
    private final DelayQueueService delayQueueService;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;
    private final PointService pointService;
    private final InviteService inviteService;
    private final MemberLevelService memberLevelService;
    private final PricingService pricingService;
    /** 领域事件发布（ITER-T06）：支付/退款事件 best-effort 发布，Outbox 同事务保证不丢。 */
    private final com.idlefish.trade.event.DomainEventPublisher eventPublisher;

    public PayService(PayOrderMapper payOrderMapper, OrderMapper orderMapper,
                      FundFlowMapper fundFlowMapper, FundEscrowService escrow,
                      ObjectMapper objectMapper,
                      @Lazy DelayQueueService delayQueueService, NotificationService notificationService,
                      MetricsRegistry metrics, PointService pointService, InviteService inviteService,
                      MemberLevelService memberLevelService, PricingService pricingService,
                      com.idlefish.trade.event.DomainEventPublisher eventPublisher) {
        this.payOrderMapper = payOrderMapper;
        this.orderMapper = orderMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.escrow = escrow;
        this.objectMapper = objectMapper;
        this.delayQueueService = delayQueueService;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.pointService = pointService;
        this.inviteService = inviteService;
        this.memberLevelService = memberLevelService;
        this.pricingService = pricingService;
        this.eventPublisher = eventPublisher;
    }

    /** 预下单：返回渠道支付参数。注入 orderId 到 MDC，使 span 与日志可按单检索（REQ-02）。 */
    public PrepayResult prepay(String orderNo) {
        TraceContext.putOrderId(orderNo);
        try {
            PayOrder po = payOrderMapper.selectOne(
                    new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOrderNo, orderNo));
            if (po == null) {
                throw new BizException(Code.ORDER_NOT_FOUND);
            }
            if (!PayStatus.WAIT.getCode().equals(po.getStatus())) {
                throw new BizException(Code.STATE_NOT_ALLOWED, "订单非待支付状态");
            }
            return metrics.timed("pay.prepay", () -> escrow.prepay(po.getPayNo(), po.getAmount()));
        } finally {
            TraceContext.clearOrderId();
        }
    }

    /** 支付结果通知（幂等 + 防伪造）：遗留表单式回调（演示/兼容）。 */
    public void notify(String payNo, String transactionId, Long amount) {
        PayOrder po = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getPayNo, payNo));
        if (po == null) {
            throw new BizException(Code.ORDER_NOT_FOUND);
        }
        // 金额一致校验：防止回调金额被篡改（资损防线）
        if (amount != null && !amount.equals(po.getAmount())) {
            throw new BizException(Code.BIZ_ERROR, "回调金额与订单金额不一致");
        }
        // 验签：Mock 直接通过；非 Mock（真实微信）必须验签，否则拒绝（防伪造支付成功）。
        // 传入完整回调上下文（payNo/transactionId/amount），供真实实现基于平台证书 + APIv3 密钥验签。
        java.util.Map<String, String> notifyParams = new java.util.HashMap<>(4);
        notifyParams.put("payNo", payNo);
        notifyParams.put("transactionId", transactionId);
        notifyParams.put("amount", amount != null ? String.valueOf(amount) : null);
        boolean verified = escrow.verifyNotify(notifyParams);
        if (!verified) {
            metrics.increment("pay.notify.verify.failure");
            throw new BizException(Code.BIZ_ERROR, "支付通知验签失败");
        }
        applyPaid(payNo, transactionId, amount);
    }

    /**
     * 微信支付 v3 JSON 回调（真实模式）：先验证节点签名，再解密 resource，最后幂等落地。
     * 仅处理 trade_state=SUCCESS；其余状态（如 REFUND/CLOSED）忽略。
     */
    public void notifyV3(String body, String timestamp, String nonce, String serial, String signature) {
        // T01 生产化：包裹回调处理全过程，输出结构化 span（REQ-04）并采集耗时，
        // 供 P99 SLO 告警（pay.callback.apply.latency）与链路追踪。
        // orderId 由 notifyV3Inner 在解密后注入 MDC，本方法负责请求级清理（避免线程复用泄漏）。
        try {
            metrics.timedRun("pay.callback.apply", () -> notifyV3Inner(body, timestamp, nonce, serial, signature));
        } finally {
            TraceContext.clearOrderId();
        }
    }

    private void notifyV3Inner(String body, String timestamp, String nonce, String serial, String signature) {
        // 真实支付：始终校验微信 v3 节点签名（已移除 mock 旁路，防伪造回调）
        boolean ok = escrow.verifySignature(timestamp, nonce, body, signature);
        if (!ok) {
            metrics.increment("pay.notify.v3.failure");
            throw new BizException(Code.BIZ_ERROR, "支付回调签名验证失败");
        }
        String resourceJson = extractResource(body);
        java.util.Map<String, Object> decoded = escrow.decryptNotify(resourceJson);
        if (decoded == null) {
            metrics.increment("pay.notify.v3.failure");
            throw new BizException(Code.BIZ_ERROR, "支付回调解密失败");
        }
        String payNo = (String) decoded.get("out_trade_no");
        String transactionId = (String) decoded.get("transaction_id");
        Object amtObj = decoded.get("amount");
        Long amount = amtObj instanceof Number ? ((Number) amtObj).longValue() : null;
        String tradeState = (String) decoded.get("trade_state");
        if (!"SUCCESS".equals(tradeState)) {
            metrics.increment("pay.notify.v3.ignored");
            return; // 非支付成功状态不处理
        }
        PayOrder po = requirePayOrder(payNo);
        TraceContext.putOrderId(po.getOrderNo()); // 注入业务单号，关联后续 span/日志（REQ-02）
        applyPaid(payNo, transactionId, amount);
        metrics.increment("pay.notify.v3.success");
    }

    /** 幂等落地支付成功（供 notify / notifyV3 复用）。
     *  采用「WAIT→SUCCESS 条件更新 + affected 行网关」：仅当支付单仍为 WAIT 时原子置 SUCCESS，
     *  并发重复回调中仅一条受影响、下游（资金流水/通知）仅执行一次，杜绝重复流水资损。 */
    private void applyPaid(String payNo, String transactionId, Long amount) {
        PayOrder po = requirePayOrder(payNo);
        if (!tryMarkPaid(payNo, transactionId)) {
            // 已处理（SUCCESS）或不存在：幂等返回，绝不重复写资金流水
            metrics.increment("pay.paid.idempotent_skipped");
            return;
        }
        metrics.increment("pay.paid");

        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, po.getOrderNo()));
        if (order != null && OrderStatus.PENDING_PAY.getCode().equals(order.getStatus())) {
            onOrderPaid(order, po);
        }

        // 资金托管在平台，买家余额不直接减（演示）
        recordFundFlow(po);

        // F-02 通知中心：支付成功触达买家（best-effort，不影响支付主流程）
        notifyBuyerPaid(po);
    }

    private PayOrder requirePayOrder(String payNo) {
        PayOrder po = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getPayNo, payNo));
        if (po == null) {
            throw new BizException(Code.ORDER_NOT_FOUND);
        }
        return po;
    }

    /** WAIT→SUCCESS 条件更新：仅当支付单仍为 WAIT 时原子置 SUCCESS，返回是否实际生效。 */
    private boolean tryMarkPaid(String payNo, String transactionId) {
        int affected = payOrderMapper.update(null, new UpdateWrapper<PayOrder>()
                .eq("pay_no", payNo)
                .eq("status", PayStatus.WAIT.getCode())
                .set("status", PayStatus.SUCCESS.getCode())
                .set("transaction_id", transactionId)
                .set("paid_at", LocalDateTime.now()));
        return affected > 0;
    }

    /** 订单置已支付，并提交 72h 未发货提醒延时任务、触达卖家。 */
    private void onOrderPaid(Order order, PayOrder po) {
        Order ou = new Order();
        ou.setId(order.getId());
        ou.setStatus(OrderStatus.PAID.getCode());
        orderMapper.updateById(ou);

        // ITER-T06：PAYMENT_SUCCEEDED（资金域，金额单位「分」，REQ-04）+ ORDER_PAID（订单域，REQ-03）
        eventPublisher.publish(com.idlefish.trade.event.EventType.PAYMENT_SUCCEEDED, po.getPayNo(), com.idlefish.trade.event.DomainEventPublisher.payload(
                "orderNo", po.getOrderNo(),
                "buyerId", po.getBuyerId(),
                "amountFen", po.getAmount(),
                "transactionId", po.getTransactionId() == null ? "" : po.getTransactionId()));
        eventPublisher.publish(com.idlefish.trade.event.EventType.ORDER_PAID, po.getOrderNo(), com.idlefish.trade.event.DomainEventPublisher.payload(
                "buyerId", order.getBuyerId(), "sellerId", order.getSellerId(),
                "amountFen", po.getAmount()));

        // D6 延时队列：支付成功后提交 72h 未发货提醒延时任务（精准到点触发；本地/RocketMQ 均走此路径）
        try {
            delayQueueService.submit("REMIND_SHIP", order.getOrderNo(), "", 72 * 60 * 60);
        } catch (Exception ignore) {
            // 延时任务提交失败不影响支付落地（定时扫描兜底提醒）
        }

        // F-05 闭环：支付成功触达卖家（您有新订单），买卖双向不漏
        notificationService.notify(order.getSellerId(), NotificationType.ORDER_PAID, po.getOrderNo(),
                "你有新订单", "订单 " + po.getOrderNo() + " 已支付成功，请尽快发货");

        // F-13.1：交易完成（支付成功）买家得积分（best-effort，不影响支付主流程）
        try {
            pointService.earnByTrade(order.getBuyerId(), order.getOrderNo(), po.getAmount());
        } catch (Exception ignore) {
        }

        // F-13.4：被邀请人首单支付成功 → 邀请人得返券奖励（best-effort，不影响支付主流程）
        try {
            inviteService.rewardFirstOrder(order.getBuyerId());
        } catch (Exception ignore) {
        }

        // F-13.2：交易成功买家得成长值（best-effort，不影响支付主流程）
        try {
            memberLevelService.addGrowth(order.getBuyerId(),
                    com.idlefish.trade.member.MemberLevelCalculator.growthFromTrade(po.getAmount()));
        } catch (Exception ignore) {
        }

        // ITER-F03：成交价回流估价样本（REQ-05，best-effort，不影响支付主流程）
        try {
            pricingService.recordDeal(order, po);
        } catch (Exception ignore) {
        }
    }

    /** 记录平台托管资金流出流水（买家余额不直接减，演示）。 */
    private void recordFundFlow(PayOrder po) {
        fundFlowMapper.insert(FundFlowBuilder.of(po.getOrderNo(), po.getBuyerId(), "OUT", "PAY", po.getAmount())
                .balanceAfter(0L).build());
    }

    /** F-02 通知中心：支付成功触达买家（best-effort，不影响支付主流程）。 */
    private void notifyBuyerPaid(PayOrder po) {
        notificationService.notify(po.getBuyerId(), NotificationType.ORDER_PAID, po.getOrderNo(),
                "支付成功", "您的订单 " + po.getOrderNo() + " 已支付成功，等待卖家发货");
    }

    private String extractResource(String body) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
            com.fasterxml.jackson.databind.JsonNode resource = root.path("resource");
            return resource.isMissingNode() ? body : resource.toString();
        } catch (Exception e) {
            return body;
        }
    }

    /** 发起退款（被 RefundService 调用）。注入 orderId，使退款 span 可按单检索（REQ-02）。 */
    public void refund(String payNo, Long amount) {
        PayOrder po = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getPayNo, payNo));
        if (po == null) {
            throw new BizException(Code.ORDER_NOT_FOUND);
        }
        TraceContext.putOrderId(po.getOrderNo());
        try {
            metrics.timed("pay.refund", () -> escrow.refund(payNo, amount));
            PayOrder upd = new PayOrder();
            upd.setId(po.getId());
            upd.setStatus(PayStatus.REFUNDED.getCode());
            payOrderMapper.updateById(upd);

            // ITER-T06：REFUND_EXECUTED 事件（资金域，REQ-04）
            eventPublisher.publish(com.idlefish.trade.event.EventType.REFUND_EXECUTED, payNo, com.idlefish.trade.event.DomainEventPublisher.payload(
                    "orderNo", po.getOrderNo(), "buyerId", po.getBuyerId(), "amountFen", amount));
        } finally {
            TraceContext.clearOrderId();
        }
    }

}
