package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    public PayService(PayOrderMapper payOrderMapper, OrderMapper orderMapper,
                      FundFlowMapper fundFlowMapper, FundEscrowService escrow,
                      ObjectMapper objectMapper, IdlefishProperties props) {
        this.payOrderMapper = payOrderMapper;
        this.orderMapper = orderMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.escrow = escrow;
        this.objectMapper = objectMapper;
        this.props = props;
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

    /** 支付结果通知（幂等 + 防伪造）。 */
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
        // 验签：Mock 直接通过；非 Mock（真实微信）必须验签，否则拒绝（防伪造支付成功）
        boolean verified = escrow.verifyNotify(java.util.Collections.singletonMap("payNo", payNo));
        if (!verified) {
            throw new BizException(Code.BIZ_ERROR, "支付通知验签失败");
        }
        if (PayStatus.SUCCESS.getCode().equals(po.getStatus())) {
            return; // 幂等：已处理
        }
        PayOrder upd = new PayOrder();
        upd.setId(po.getId());
        upd.setStatus(PayStatus.SUCCESS.getCode());
        upd.setTransactionId(transactionId);
        upd.setPaidAt(LocalDateTime.now());
        payOrderMapper.updateById(upd);

        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, po.getOrderNo()));
        if (order != null && OrderStatus.PENDING_PAY.getCode().equals(order.getStatus())) {
            Order ou = new Order();
            ou.setId(order.getId());
            ou.setStatus(OrderStatus.PAID.getCode());
            orderMapper.updateById(ou);
        }

        FundFlow ff = new FundFlow();
        ff.setBizNo(po.getOrderNo());
        ff.setUserId(po.getBuyerId());
        ff.setDirection("OUT");
        ff.setAmount(po.getAmount());
        ff.setType("PAY");
        ff.setBalanceAfter(0L); // 资金托管在平台，买家余额不直接减（演示）
        fundFlowMapper.insert(ff);
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
