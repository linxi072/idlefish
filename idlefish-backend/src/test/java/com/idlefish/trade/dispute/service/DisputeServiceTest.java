package com.idlefish.trade.dispute.service;

import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.dispute.DisputeStateMachine;
import com.idlefish.trade.dispute.entity.Dispute;
import com.idlefish.trade.dispute.mapper.DisputeMapper;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 维权工单服务单测：聚焦 create / resolve 与 DISPUTE_OPENED / DISPUTE_RESOLVED 领域事件接线（事件驱动架构 T06E）。
 *
 * <p>采用纯 Mockito 离线范式；通知为 best-effort（内部 try/catch），无需对其打桩。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeServiceTest {

    private static final Long BUYER = 1L;
    private static final Long SELLER = 2L;
    private static final String ORDER_NO = "ORD1";

    @Mock private DisputeMapper disputeMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private NotificationService notificationService;
    @Mock private MetricsRegistry metrics;
    @Mock private com.idlefish.trade.event.DomainEventPublisher eventPublisher;

    @InjectMocks
    private DisputeService service;

    @Test
    void create_publishesDisputeOpenedEvent() {
        Order o = new Order();
        o.setOrderNo(ORDER_NO);
        o.setBuyerId(BUYER);
        o.setSellerId(SELLER);
        o.setPayAmount(100_00L);
        when(orderMapper.selectOne(any())).thenReturn(o);
        when(disputeMapper.selectCount(any())).thenReturn(0L); // 无未结工单
        // 模拟 MyBatis-Plus ASSIGN_ID：insert 前回填主键，使 d.getId() 非空
        doAnswer(inv -> {
            ((Dispute) inv.getArgument(0)).setId(1L);
            return 1;
        }).when(disputeMapper).insert((Dispute) any());

        Long id = service.create(BUYER, ORDER_NO, DisputeService.TYPE_NOT_RECEIVED,
                DisputeService.EXPECT_REFUND, "未收到货", null, null);

        assertNotNull(id);
        verify(disputeMapper, times(1)).insert(any(Dispute.class));
        verify(metrics, times(1)).increment("dispute.created");
        // 工单创建即可见：发布 DISPUTE_OPENED（聚合根 = 工单号）
        verify(eventPublisher, times(1)).publish(
                eq(com.idlefish.trade.event.EventType.DISPUTE_OPENED),
                any(), any());
    }

    @Test
    void resolve_publishesDisputeResolvedEvent() {
        Dispute d = new Dispute();
        d.setId(1L);
        d.setDisputeNo("DP1");
        d.setOrderNo(ORDER_NO);
        d.setBuyerId(BUYER);
        d.setSellerId(SELLER);
        d.setAmount(100_00L);
        d.setStatus(DisputeStateMachine.PENDING); // 可裁决
        when(disputeMapper.selectById(1L)).thenReturn(d);
        when(disputeMapper.updateById((Dispute) any())).thenReturn(1);

        service.resolve(1L, DisputeService.RESULT_BUYER_WIN, 50_00L, "支持买家");

        verify(disputeMapper, times(1)).updateById(any(Dispute.class));
        verify(metrics, times(1)).increment("dispute.resolved");
        // 裁决终态：发布 DISPUTE_RESOLVED（聚合根 = 工单号，供退款/信用/通知下游消费）
        verify(eventPublisher, times(1)).publish(
                eq(com.idlefish.trade.event.EventType.DISPUTE_RESOLVED),
                any(), any());
    }
}
