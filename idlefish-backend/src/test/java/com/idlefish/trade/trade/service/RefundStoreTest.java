package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.enums.RefundStatus;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.Refund;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.RefundMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款数据访问单测（REQ-07）：Mockito 离线，验证 RefundStore 对 3 个 Mapper 的读/写与 CAS 推进正确性。
 * 无 Spring 上下文需初始化 MyBatis-Plus 实体元数据，否则 LambdaQueryWrapper 解析失败。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundStoreTest {

    @Mock
    private RefundMapper refundMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private FundFlowMapper fundFlowMapper;

    private RefundStore store;

    @BeforeAll
    static void initMpMetadata() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "dummy");
        TableInfoHelper.initTableInfo(assistant, Refund.class);
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, FundFlow.class);
    }

    @BeforeEach
    void init() {
        store = new RefundStore(refundMapper, orderMapper, fundFlowMapper);
    }

    private static Refund refund(String no, String status) {
        Refund r = new Refund();
        r.setId(100L);
        r.setRefundNo(no);
        r.setOrderNo("ORD1");
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return r;
    }

    private static Order order(Long id) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNo("ORD1");
        o.setStatus("paid");
        return o;
    }

    // ===================== 读 =====================

    @Test
    void selectOneByRefundNo_delegates() {
        Refund r = refund("RF1", "wait_seller");
        when(refundMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Refund>>any())).thenReturn(r);
        assertEquals("RF1", store.selectOneByRefundNo("RF1").getRefundNo());
    }

    @Test
    void selectOneByRefundNo_nullWhenAbsent() {
        when(refundMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Refund>>any())).thenReturn(null);
        assertNull(store.selectOneByRefundNo("X"));
    }

    @Test
    void selectActiveByOrderNo_returnsMatch() {
        Refund r = refund("RF1", "wait_seller");
        when(refundMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Refund>>any())).thenReturn(r);
        assertEquals("RF1", store.selectActiveByOrderNo("ORD1").getRefundNo());
    }

    @Test
    void selectByOrderNo_returnsList() {
        Refund a = refund("RFA", "wait_seller");
        Refund b = refund("RFB", "refunded");
        when(refundMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<Refund>>any())).thenReturn(List.of(a, b));
        assertEquals(2, store.selectByOrderNo("ORD1").size());
    }

    @Test
    void selectLatestByOrderNo_returnsOne() {
        Refund r = refund("RFL", "wait_seller");
        when(refundMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Refund>>any())).thenReturn(r);
        assertEquals("RFL", store.selectLatestByOrderNo("ORD1").getRefundNo());
    }

    @Test
    void selectOrderByOrderNo_delegates() {
        Order o = order(9L);
        when(orderMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Order>>any())).thenReturn(o);
        assertEquals(9L, store.selectOrderByOrderNo("ORD1").getId());
    }

    @Test
    void selectOrderByOrderNo_nullWhenAbsent() {
        when(orderMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Order>>any())).thenReturn(null);
        assertNull(store.selectOrderByOrderNo("X"));
    }

    @Test
    void selectAutoAgreeDue_filtersByTime() {
        Refund due = refund("RF_DUE", "wait_seller");
        when(refundMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<Refund>>any())).thenReturn(List.of(due));
        List<Refund> list = store.selectAutoAgreeDue(LocalDateTime.of(2030, 1, 1, 0, 0));
        assertEquals(1, list.size());
        assertEquals("RF_DUE", list.get(0).getRefundNo());
    }

    @Test
    void selectAutoPlatformDue_filtersByTime() {
        Refund due = refund("RF_PF", "wait_seller");
        when(refundMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<Refund>>any())).thenReturn(List.of(due));
        List<Refund> list = store.selectAutoPlatformDue(LocalDateTime.of(2030, 1, 1, 0, 0));
        assertEquals(1, list.size());
        assertEquals("RF_PF", list.get(0).getRefundNo());
    }

    // ===================== 写 =====================

    @Test
    void insertRefund_delegatesToMapper() {
        store.insertRefund(refund("RF1", "wait_seller"));
        verify(refundMapper).insert((Refund) any());
    }

    @Test
    void updateById_delegatesToMapper() {
        store.updateById(refund("RF1", "rejected"));
        verify(refundMapper).updateById((Refund) any());
    }

    @Test
    void casToRefunding_setsRefundingAndReturnsAffected() {
        when(refundMapper.update((Refund) any(), any(LambdaQueryWrapper.class))).thenReturn(1);
        assertEquals(1, store.casToRefunding(100L));
        ArgumentCaptor<Refund> cap = ArgumentCaptor.forClass(Refund.class);
        verify(refundMapper).update(cap.capture(), any(LambdaQueryWrapper.class));
        assertEquals(RefundStatus.REFUNDING.getCode(), cap.getValue().getStatus());
    }

    @Test
    void casToRefunding_returnsZeroWhenNotAffected() {
        when(refundMapper.update((Refund) any(), any(LambdaQueryWrapper.class))).thenReturn(0);
        assertEquals(0, store.casToRefunding(100L));
    }

    @Test
    void casToRefunded_setsRefundedStatusAndRefundAt() {
        when(refundMapper.update((Refund) any(), any(LambdaQueryWrapper.class))).thenReturn(1);
        LocalDateTime now = LocalDateTime.of(2026, 5, 5, 5, 5, 5);
        assertEquals(1, store.casToRefunded(100L, now));
        ArgumentCaptor<Refund> cap = ArgumentCaptor.forClass(Refund.class);
        verify(refundMapper).update(cap.capture(), any(LambdaQueryWrapper.class));
        assertEquals(RefundStatus.REFUNDED.getCode(), cap.getValue().getStatus());
        assertEquals(now, cap.getValue().getRefundAt());
    }

    @Test
    void insertFundFlow_delegatesToMapper() {
        store.insertFundFlow(new FundFlow());
        verify(fundFlowMapper).insert((FundFlow) any());
    }

    @Test
    void closeOrder_setsClosedStatus() {
        store.closeOrder(9L);
        ArgumentCaptor<Order> cap = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(cap.capture());
        assertEquals(9L, cap.getValue().getId());
        assertEquals(OrderStatus.CLOSED.getCode(), cap.getValue().getStatus());
    }
}
