package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.enums.PayStatus;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.trade.service.DelayQueueService;
import com.idlefish.trade.notify.enums.NotificationType;
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
    private final IdlefishProperties props;
    private final DelayQueueService delayQueueService;
    private final NotificationService notificationService;

    public PayService(PayOrderMapper payOrderMapper, OrderMapper orderMapper,
                      FundFlowMapper fundFlowMapper, FundEscrowService escrow,
                      ObjectMapper objectMapper, IdlefishProperties props,
                      @Lazy DelayQueueService delayQueueService, NotificationService notificationService) {
        this.payOrderMapper = payOrderMapper;
        this.orderMapper = orderMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.escrow = escrow;
        this.objectMapper = objectMapper;
        this.props = props;
        this.delayQueueService = delayQueueService;
        this.notificationService = notificationService;
    }

    /** 预下单：返回渠道支付参数。 */
    public PrepayResult prepay(String orderNo) {
        PayOrder po = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOrderNo, orderNo));
        if (po == null) {
            throw new BizException(Code.ORDER_NOT_FOUND);
        }
        if (!PayStatus.WAIT.getCode().equals(po.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "订单非待支付状态");
        }
        return escrow.prepay(po.getPayNo(), po.getAmount());
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
            throw new BizException(Code.BIZ_ERROR, "支付通知验签失败");
        }
        applyPaid(payNo, transactionId, amount);
    }

    /**
     * 微信支付 v3 JSON 回调（真实模式）：先验证节点签名，再解密 resource，最后幂等落地。
     * 仅处理 trade_state=SUCCESS；其余状态（如 REFUND/CLOSED）忽略。
     */
    public void notifyV3(String body, String timestamp, String nonce, String serial, String signature) {
        if (!props.isPayMock()) {
            boolean ok = escrow.verifySignature(timestamp, nonce, body, signature);
            if (!ok) {
                throw new BizException(Code.BIZ_ERROR, "支付回调签名验证失败");
            }
        }
        String resourceJson = extractResource(body);
        java.util.Map<String, Object> decoded = escrow.decryptNotify(resourceJson);
        if (decoded == null) {
            throw new BizException(Code.BIZ_ERROR, "支付回调解密失败");
        }
        String payNo = (String) decoded.get("out_trade_no");
        String transactionId = (String) decoded.get("transaction_id");
        Object amtObj = decoded.get("amount");
        Long amount = amtObj instanceof Number ? ((Number) amtObj).longValue() : null;
        String tradeState = (String) decoded.get("trade_state");
        if (!"SUCCESS".equals(tradeState)) {
            return; // 非支付成功状态不处理
        }
        applyPaid(payNo, transactionId, amount);
    }

    /** 幂等落地支付成功（供 notify / notifyV3 复用）。
     *  采用「WAIT→SUCCESS 条件更新 + affected 行网关」：仅当支付单仍为 WAIT 时原子置 SUCCESS，
     *  并发重复回调中仅一条受影响、下游（资金流水/通知）仅执行一次，杜绝重复流水资损。 */
    private void applyPaid(String payNo, String transactionId, Long amount) {
        PayOrder po = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getPayNo, payNo));
        if (po == null) {
            throw new BizException(Code.ORDER_NOT_FOUND);
        }
        int affected = payOrderMapper.update(null, new UpdateWrapper<PayOrder>()
                .eq("pay_no", payNo)
                .eq("status", PayStatus.WAIT.getCode())
                .set("status", PayStatus.SUCCESS.getCode())
                .set("transaction_id", transactionId)
                .set("paid_at", LocalDateTime.now()));
        if (affected == 0) {
            return; // 已处理（SUCCESS）或不存在：幂等返回，绝不重复写资金流水
        }

        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, po.getOrderNo()));
        if (order != null && OrderStatus.PENDING_PAY.getCode().equals(order.getStatus())) {
            Order ou = new Order();
            ou.setId(order.getId());
            ou.setStatus(OrderStatus.PAID.getCode());
            orderMapper.updateById(ou);

            // D6 延时队列：支付成功后提交 72h 未发货提醒延时任务（精准到点触发；本地/RocketMQ 均走此路径）
            try {
                delayQueueService.submit("REMIND_SHIP", order.getOrderNo(), "", 72 * 60 * 60);
            } catch (Exception ignore) {
                // 延时任务提交失败不影响支付落地（定时扫描兜底提醒）
            }

            // F-05 闭环：支付成功触达卖家（您有新订单），买卖双向不漏
            notificationService.notify(order.getSellerId(), NotificationType.ORDER_PAID, po.getOrderNo(),
                    "你有新订单", "订单 " + po.getOrderNo() + " 已支付成功，请尽快发货");
        }

        FundFlow ff = new FundFlow();
        ff.setBizNo(po.getOrderNo());
        ff.setUserId(po.getBuyerId());
        ff.setDirection("OUT");
        ff.setAmount(po.getAmount());
        ff.setType("PAY");
        ff.setBalanceAfter(0L); // 资金托管在平台，买家余额不直接减（演示）
        fundFlowMapper.insert(ff);

        // F-02 通知中心：支付成功触达买家（best-effort，不影响支付主流程）
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

    /** 发起退款（被 RefundService 调用）。 */
    public void refund(String payNo, Long amount) {
        String refundId = escrow.refund(payNo, amount);
        PayOrder po = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getPayNo, payNo));
        if (po == null) {
            throw new BizException(Code.ORDER_NOT_FOUND);
        }
        PayOrder upd = new PayOrder();
        upd.setId(po.getId());
        upd.setStatus(PayStatus.REFUNDED.getCode());
        payOrderMapper.updateById(upd);
    }

    /**
     * Mock 支付完成（仅 idlefish.pay.mock=true 可用），用于演示链路跑通。
     * 复用 {@link #notify} 的幂等与验签逻辑，避免重复代码；真实模式禁止手动完成支付。
     */
    public void mockComplete(String payNo) {
        if (!props.isPayMock()) {
            throw new BizException(Code.FORBIDDEN, "仅 Mock 支付模式可手动完成支付");
        }
        String transactionId = "MOCK" + System.nanoTime();
        notify(payNo, transactionId, null);
    }
}
