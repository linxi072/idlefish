package com.idlefish.trade.trade.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.enums.PayStatus;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.trade.service.DelayQueueService;
import com.idlefish.trade.trade.service.FundEscrowService;
import com.idlefish.trade.notify.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付回流链路单测（F-10 验证项）：幂等 / 金额校验 / 订单状态同步 / 双向通知。
 * Mock 全部依赖，无 Spring、无 DB，离线可跑（mvn -o -Plocal test）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayCallbackTest {

    @Mock private PayOrderMapper payOrderMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private FundFlowMapper fundFlowMapper;
    @Mock private FundEscrowService escrow;
    @Mock private IdlefishProperties props;
    @Mock private DelayQueueService delayQueueService;
    @Mock private NotificationService notificationService;
    @InjectMocks private PayService payService;

    private final AtomicReference<String> payStatus = new AtomicReference<>(PayStatus.WAIT.getCode());
    private final AtomicReference<String> orderStatus = new AtomicReference<>(OrderStatus.PENDING_PAY.getCode());
    private final AtomicInteger flowInserts = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        payStatus.set(PayStatus.WAIT.getCode());
        orderStatus.set(OrderStatus.PENDING_PAY.getCode());
        flowInserts.set(0);

        when(payOrderMapper.selectOne(any())).thenAnswer(inv -> {
            PayOrder p = new PayOrder();
            p.setId(1L); p.setPayNo("PAY1"); p.setOrderNo("NO1"); p.setAmount(1000L);
            p.setBuyerId(2001L); p.setStatus(payStatus.get());
            return p;
        });
        when(payOrderMapper.updateById(any(PayOrder.class))).thenAnswer(inv -> {
            PayOrder u = inv.getArgument(0);
            payStatus.set(u.getStatus());
            return 1;
        });
        // 条件更新（WAIT→SUCCESS）的桩：仅当当前为 WAIT 时才置 SUCCESS 并返回 1，
        // 模拟并发重复回调时第二条受影响行为 0，从而下游（资金流水）仅写入一次。
        when(payOrderMapper.update(any(), any())).thenAnswer(inv -> {
            if (PayStatus.WAIT.getCode().equals(payStatus.get())) {
                payStatus.set(PayStatus.SUCCESS.getCode());
                return 1;
            }
            return 0;
        });
        when(orderMapper.selectOne(any())).thenAnswer(inv -> {
            Order o = new Order();
            o.setId(1L); o.setOrderNo("NO1"); o.setBuyerId(2001L); o.setSellerId(1001L);
            o.setStatus(orderStatus.get());
            return o;
        });
        when(orderMapper.updateById(any(Order.class))).thenAnswer(inv -> {
            Order u = inv.getArgument(0);
            orderStatus.set(u.getStatus());
            return 1;
        });
        when(fundFlowMapper.insert(any(FundFlow.class))).thenAnswer(inv -> {
            flowInserts.incrementAndGet();
            return 1;
        });
        when(escrow.verifyNotify(any())).thenReturn(true);
        doNothing().when(delayQueueService).submit(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("支付回调幂等：重复回调仅落地一次（资金流水 1 条、订单仅同步一次）")
    void idempotentCallback() {
        payService.notify("PAY1", "TX1", null);
        payService.notify("PAY1", "TX1", null);

        assertEquals(PayStatus.SUCCESS.getCode(), payStatus.get());
        assertEquals(OrderStatus.PAID.getCode(), orderStatus.get());
        assertEquals(1, flowInserts.get());
    }

    @Test
    @DisplayName("金额校验：回调金额与订单不一致则拒绝（防资损）")
    void amountMismatchRejected() {
        BizException ex = assertThrows(BizException.class, () -> payService.notify("PAY1", "TX1", 600L));
        assertEquals(Code.BIZ_ERROR.getCode(), ex.getCode());
        assertEquals(0, flowInserts.get());
    }

    @Test
    @DisplayName("订单状态同步：回调后订单由待支付转为已支付")
    void orderStatusSynced() {
        payService.notify("PAY1", "TX1", null);
        assertEquals(OrderStatus.PAID.getCode(), orderStatus.get());
        // 双向通知：买家 + 卖家 各一次
        verify(notificationService, times(2)).notify(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("微信 v3 回调：验签通过 + 解密 + 状态过滤后正确落地")
    void wechatV3Callback() {
        when(props.isPayMock()).thenReturn(false);
        when(escrow.verifySignature(any(), any(), any(), any())).thenReturn(true);
        java.util.Map<String, Object> decoded = new java.util.HashMap<>();
        decoded.put("out_trade_no", "PAY1");
        decoded.put("transaction_id", "T1");
        decoded.put("amount", 1000);
        decoded.put("trade_state", "SUCCESS");
        when(escrow.decryptNotify(any())).thenReturn(decoded);

        payService.notifyV3("{\"out_trade_no\":\"PAY1\"}", "ts", "nonce", "serial", "sig");

        assertEquals(PayStatus.SUCCESS.getCode(), payStatus.get());
        assertEquals(OrderStatus.PAID.getCode(), orderStatus.get());
    }
}
