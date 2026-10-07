package com.idlefish.trade.trade.service;

import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.idempotent.IdempotentService;
import com.idlefish.trade.common.lock.DistributedLock;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.marketing.service.CouponService;
import com.idlefish.trade.marketing.service.PointService;
import com.idlefish.trade.trade.service.ActivityService;
import com.idlefish.trade.inspection.service.InspectionService;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.risk.service.TrackService;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.LogisticsMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.user.service.AddressService;
import com.idlefish.trade.user.service.CreditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动确认收货单测（P0-4）：聚焦「确认收货→结算」的并发安全。
 * 业务逻辑依赖 @Version 乐观锁（OCC 已启用），当且仅当 updateById 的 affected==1 才触发结算；
 * 本测试以 mock 直接模拟 OCC 命中（返回 1）与失配（返回 0），验证：
 *  1) 命中时正常结算；2) 并发失配时跳过结算（杜绝重复结算资损）；3) 非运输中状态提前返回。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceConfirmReceiveTest {

    @Mock private OrderMapper orderMapper;
    @Mock private PayOrderMapper payOrderMapper;
    @Mock private LogisticsMapper logisticsMapper;
    @Mock private ItemService itemService;
    @Mock private ItemMapper itemMapper;
    @Mock private AddressService addressService;
    @Mock private LogisticService logisticService;
    @Mock private SettlementService settlementService;
    @Mock private TrackService trackService;
    @Mock private DelayQueueService delayQueueService;
    @Mock private NotificationService notificationService;
    @Mock private CreditService creditService;
    @Mock private CouponService couponService;
    @Mock private PointService pointService;
    @Mock private ActivityService activityService;
    @Mock private InspectionService inspectionService;
    @Mock private IdempotentService idempotentService;
    @Mock private DistributedLock distributedLock;
    @Mock private PlatformTransactionManager txManager;

    private OrderService service() {
        return new OrderService(orderMapper, payOrderMapper, logisticsMapper, itemService, itemMapper,
                addressService, logisticService, settlementService, trackService, new ObjectMapper(),
                delayQueueService, notificationService, creditService, couponService, pointService,
                activityService, idempotentService, distributedLock, inspectionService, txManager);
    }

    private Order shippingOrder() {
        Order o = new Order();
        o.setId(1L);
        o.setOrderNo("ORD1");
        o.setBuyerId(1L);
        o.setSellerId(2L);
        o.setItemId(10L);
        o.setStatus(OrderStatus.SHIPPING.getCode());
        o.setVersion(1);
        return o;
    }

    @Test
    void confirmReceiveSingle_success_triggersSettlement() {
        Order o = shippingOrder();
        when(orderMapper.selectOne(any())).thenReturn(o);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1); // OCC 命中（version 匹配）

        service().confirmReceiveSingle("ORD1");

        verify(settlementService, times(1)).onTradeSuccess("ORD1");
        verify(itemService, times(1)).markSold(10L);
    }

    /**
     * P0-4 核心回归：并发下「手动确认」已先行将订单推进为已完成（version 已被其占用），
     * 自动确认此处 updateById 失配返回 0，必须跳过结算，避免重复结算资损。
     */
    @Test
    void confirmReceiveSingle_versionConflict_skipsSettlement() {
        Order o = shippingOrder();
        when(orderMapper.selectOne(any())).thenReturn(o);
        when(orderMapper.updateById(any(Order.class))).thenReturn(0); // OCC 失配（已被其它线程/节点确认）

        service().confirmReceiveSingle("ORD1");

        verify(orderMapper, times(1)).updateById(any(Order.class));
        verify(settlementService, never()).onTradeSuccess(anyString());
        verify(itemService, never()).markSold(anyLong());
    }

    @Test
    void confirmReceiveSingle_notShipping_returnsEarly() {
        Order o = shippingOrder();
        o.setStatus(OrderStatus.COMPLETED.getCode());
        when(orderMapper.selectOne(any())).thenReturn(o);

        service().confirmReceiveSingle("ORD1");

        verify(orderMapper, never()).updateById(any(Order.class));
        verify(settlementService, never()).onTradeSuccess(anyString());
    }
}
