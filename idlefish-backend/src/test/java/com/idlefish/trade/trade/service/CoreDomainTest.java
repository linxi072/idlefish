package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.enums.PayStatus;
import com.idlefish.trade.common.enums.RefundStatus;
import com.idlefish.trade.trade.dto.RefundApplyDTO;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.entity.Refund;
import com.idlefish.trade.trade.entity.Settlement;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.trade.mapper.RefundMapper;
import com.idlefish.trade.trade.mapper.SettlementMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 核心交易域不变量测试（F-03 测试与 CI）。
 *
 * 覆盖四大资损/正确性防线：
 * 1. 结算幂等 + 金额换算（5% 平台佣金精确）
 * 2. T+1 结算到期放款 + 卖家入账流水
 * 3. 退款状态机（apply→agree→refunded）与状态守卫（rejected 后不可 agree/cancel）
 * 4. 乐观锁 CAS（过期版本号更新被拦截，杜绝并发双重结算/状态篡改）
 *
 * 运行环境：MySQL（schema-mysql.sql + data-mysql.sql 自动初始化），默认 Mock 外部依赖。
 */
@SpringBootTest
class CoreDomainTest {

    @Autowired
    private SettlementService settlementService;
    @Autowired
    private RefundService refundService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RefundMapper refundMapper;
    @Autowired
    private SettlementMapper settlementMapper;
    @Autowired
    private FundFlowMapper fundFlowMapper;
    @Autowired
    private PayOrderMapper payOrderMapper;

    private String uniq(String prefix) {
        return prefix + System.nanoTime() + "_" + Thread.currentThread().getId();
    }

    private Order newPaidOrder(String orderNo, String payNo, long payAmount) {
        Order o = new Order();
        o.setOrderNo(orderNo);
        o.setBuyerId(1001L);
        o.setSellerId(2001L);
        o.setItemId(3001L);
        o.setPayAmount(payAmount);
        o.setPayNo(payNo);
        o.setStatus(OrderStatus.PAID.getCode());
        o.setVersion(0);
        orderMapper.insert(o);
        return o;
    }

    private void newPayOrder(String payNo, String orderNo, long amount) {
        PayOrder po = new PayOrder();
        po.setPayNo(payNo);
        po.setOrderNo(orderNo);
        po.setBuyerId(1001L);
        po.setAmount(amount);
        po.setChannel("wechat");
        po.setStatus(PayStatus.WAIT.getCode());
        payOrderMapper.insert(po);
    }

    @Test
    @DisplayName("结算幂等 + 金额换算：同订单仅生成一次，佣金精确为 5%")
    void settlementIdempotencyAndAmountSplit() {
        String orderNo = uniq("NO");
        long payAmount = 10000L; // 100 元
        newPaidOrder(orderNo, uniq("PAY"), payAmount);

        settlementService.onTradeSuccess(orderNo);
        settlementService.onTradeSuccess(orderNo); // 幂等：第二次应直接返回

        List<Settlement> list = settlementMapper.selectList(
                new LambdaQueryWrapper<Settlement>().eq(Settlement::getOrderNo, orderNo));
        assertEquals(1, list.size(), "结算单应幂等，仅生成一条");
        Settlement s = list.get(0);
        assertEquals(500L, s.getPlatformFee(), "平台佣金应为 5%（500 分）");
        assertEquals(9500L, s.getAmount(), "卖家应收应为 9500 分（10000 - 500）");
        assertEquals("pending", s.getStatus());
    }

    @Test
    @DisplayName("T+1 结算到期放款：pending → settled 并生成卖家入账流水")
    void settlementDueDisbursement() {
        String orderNo = uniq("NO");
        String payNo = uniq("PAY");
        long payAmount = 20000L; // 200 元
        newPaidOrder(orderNo, payNo, payAmount);

        settlementService.onTradeSuccess(orderNo);
        Settlement s = settlementMapper.selectOne(
                new LambdaQueryWrapper<Settlement>().eq(Settlement::getOrderNo, orderNo));
        assertNotNull(s);

        // 模拟 T+1 到期：将计划结算时间改为已过期
        Settlement upd = new Settlement();
        upd.setId(s.getId());
        upd.setSettleAt(LocalDateTime.now().minusMinutes(1));
        settlementMapper.updateById(upd);

        settlementService.processDue();

        Settlement after = settlementMapper.selectById(s.getId());
        assertEquals("settled", after.getStatus(), "到期后应放款为 settled");
        FundFlow ff = fundFlowMapper.selectOne(
                new LambdaQueryWrapper<FundFlow>().eq(FundFlow::getBizNo, after.getSettleNo()));
        assertNotNull(ff, "应生成卖家入账流水");
        assertEquals("IN", ff.getDirection());
        assertEquals(s.getAmount(), ff.getAmount());
    }

    @Test
    @DisplayName("退款状态机：apply → agree → refunded，生成退款流水并关单")
    void refundStateMachineHappyPath() {
        String orderNo = uniq("NO");
        String payNo = uniq("PAY");
        long payAmount = 5000L;
        newPaidOrder(orderNo, payNo, payAmount);
        newPayOrder(payNo, orderNo, payAmount);

        RefundApplyDTO dto = new RefundApplyDTO();
        dto.setOrderNo(orderNo);
        dto.setType("only_refund");
        dto.setAmount(payAmount);
        String refundNo = refundService.apply(1001L, dto);

        Refund r = refundMapper.selectOne(
                new LambdaQueryWrapper<Refund>().eq(Refund::getRefundNo, refundNo));
        assertEquals(RefundStatus.WAIT_SELLER.getCode(), r.getStatus());

        refundService.agree(2001L, refundNo);
        Refund r2 = refundMapper.selectOne(
                new LambdaQueryWrapper<Refund>().eq(Refund::getRefundNo, refundNo));
        assertEquals(RefundStatus.REFUNDED.getCode(), r2.getStatus(), "卖家同意后应退款成功");

        FundFlow ff = fundFlowMapper.selectOne(
                new LambdaQueryWrapper<FundFlow>().eq(FundFlow::getBizNo, refundNo));
        assertNotNull(ff, "应生成退款流水");
        assertEquals("REFUND", ff.getType());
        assertEquals("IN", ff.getDirection());

        Order o2 = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        assertEquals(OrderStatus.CLOSED.getCode(), o2.getStatus(), "退款成功后订单应关闭");
    }

    @Test
    @DisplayName("退款状态守卫：rejected 后不可 agree 亦不可 cancel")
    void refundStateGuard() {
        String orderNo = uniq("NO");
        String payNo = uniq("PAY");
        long payAmount = 5000L;
        newPaidOrder(orderNo, payNo, payAmount);
        newPayOrder(payNo, orderNo, payAmount);

        RefundApplyDTO dto = new RefundApplyDTO();
        dto.setOrderNo(orderNo);
        dto.setType("only_refund");
        dto.setAmount(payAmount);
        String refundNo = refundService.apply(1001L, dto);

        refundService.reject(2001L, refundNo, "不想要了");
        Refund r = refundMapper.selectOne(
                new LambdaQueryWrapper<Refund>().eq(Refund::getRefundNo, refundNo));
        assertEquals(RefundStatus.REJECTED.getCode(), r.getStatus());

        BizException ex1 = assertThrows(BizException.class, () -> refundService.agree(2001L, refundNo));
        assertEquals(Code.STATE_NOT_ALLOWED.getCode(), ex1.getCode(), "rejected 不可同意");

        BizException ex2 = assertThrows(BizException.class, () -> refundService.cancel(1001L, refundNo));
        assertEquals(Code.STATE_NOT_ALLOWED.getCode(), ex2.getCode(), "rejected 不可撤销");
    }

    @Test
    @DisplayName("乐观锁 CAS：携带过期版本号更新订单返回 0 行，状态不被篡改")
    void optimisticLockCas() {
        Order o = new Order();
        o.setOrderNo(uniq("NO"));
        o.setBuyerId(1001L);
        o.setSellerId(2001L);
        o.setItemId(3001L);
        o.setPayAmount(1000L);
        o.setPayNo(uniq("PAY"));
        o.setStatus(OrderStatus.PAID.getCode());
        o.setVersion(0);
        orderMapper.insert(o);

        Order loaded = orderMapper.selectById(o.getId());
        assertEquals(Integer.valueOf(0), loaded.getVersion(), "初始版本应为 0");

        // 第一次更新（版本 0 → 1）应成功
        Order upd1 = new Order();
        upd1.setId(o.getId());
        upd1.setVersion(0);
        upd1.setStatus(OrderStatus.SHIPPING.getCode());
        int affected1 = orderMapper.updateById(upd1);
        assertEquals(1, affected1);

        // 模拟并发：另一事务已提交，DB 版本变为 1；本事务仍携带过期版本 0 → CAS 失败
        Order loaded2 = orderMapper.selectById(o.getId());
        assertEquals(Integer.valueOf(1), loaded2.getVersion());

        Order upd2 = new Order();
        upd2.setId(o.getId());
        upd2.setVersion(0);
        upd2.setStatus(OrderStatus.COMPLETED.getCode());
        int affected2 = orderMapper.updateById(upd2);
        assertEquals(0, affected2, "过期版本更新必须被乐观锁拦截");

        Order loaded3 = orderMapper.selectById(o.getId());
        assertEquals(OrderStatus.SHIPPING.getCode(), loaded3.getStatus(), "状态不应被过期更新篡改");
        assertEquals(Integer.valueOf(1), loaded3.getVersion());
    }
}
