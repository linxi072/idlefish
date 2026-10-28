package com.idlefish.trade.workorder.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.event.DomainEventPublisher;
import com.idlefish.trade.event.EventType;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.workorder.WorkOrderStateMachine;
import com.idlefish.trade.workorder.entity.WorkOrder;
import com.idlefish.trade.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客服工单服务单测：聚焦 create / assign / transition / closeByUser 与领域事件、通知接线。
 *
 * <p>采用纯 Mockito 离线范式；通知为 best-effort（内部 try/catch），无需对其打桩。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderServiceTest {

    private static final Long USER = 1L;
    private static final Long ADMIN = 99L;
    private static final Long ASSIGNEE = 88L;

    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private NotificationService notificationService;
    @Mock private MetricsRegistry metrics;
    @Mock private DomainEventPublisher eventPublisher;

    @InjectMocks
    private WorkOrderService service;

    @Test
    void create_publishesWorkOrderOpenedEvent() {
        when(workOrderMapper.insert(any(WorkOrder.class))).thenAnswer(inv -> {
            ((WorkOrder) inv.getArgument(0)).setId(1L);
            return 1;
        });
        Long id = service.create(USER, WorkOrderService.TYPE_REFUND, WorkOrderService.PRIORITY_HIGH,
                WorkOrderService.CHANNEL_APP, "退款未到账", "下单后未收到退款", "ORD1");
        assertNotNull(id);
        verify(workOrderMapper, times(1)).insert(any(WorkOrder.class));
        verify(metrics, times(1)).increment("workorder.created");
        verify(eventPublisher, times(1)).publish(eq(EventType.WORKORDER_OPENED), anyString(), any());
    }

    @Test
    void create_invalidType_throws() {
        assertThrows(BizException.class, () -> service.create(USER, "BOGUS",
                WorkOrderService.PRIORITY_HIGH, WorkOrderService.CHANNEL_APP, "标题", "描述", null));
    }

    @Test
    void assign_setsInProgressAndNotifiesReporter() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setTicketNo("WO1");
        wo.setUserId(USER);
        wo.setStatus(WorkOrderStateMachine.OPEN);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

        service.assign(ADMIN, 1L, ASSIGNEE);

        ArgumentCaptor<WorkOrder> cap1 = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper, times(1)).updateById(cap1.capture());
        WorkOrder saved = cap1.getValue();
        assertEquals(WorkOrderStateMachine.IN_PROGRESS, saved.getStatus());
        assertEquals(ASSIGNEE, saved.getAssigneeId());
        assertNotNull(saved.getFirstResponseAt());
        verify(metrics, times(1)).increment("workorder.assigned");
        verify(notificationService, times(1)).notify(eq(USER),
                eq(NotificationType.WORKORDER_ASSIGNED), anyString(), anyString(), anyString());
    }

    @Test
    void transitionToResolved_requiresResolutionAndNotifies() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setTicketNo("WO1");
        wo.setUserId(USER);
        wo.setAssigneeId(ASSIGNEE);
        wo.setStatus(WorkOrderStateMachine.IN_PROGRESS);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

        service.transition(ADMIN, 1L, WorkOrderStateMachine.RESOLVED, "已补发退款");

        ArgumentCaptor<WorkOrder> cap2 = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper, times(1)).updateById(cap2.capture());
        WorkOrder saved2 = cap2.getValue();
        assertEquals(WorkOrderStateMachine.RESOLVED, saved2.getStatus());
        assertEquals("已补发退款", saved2.getResolution());
        assertNotNull(saved2.getResolvedAt());
        verify(eventPublisher, times(1)).publish(eq(EventType.WORKORDER_RESOLVED), anyString(), any());
        verify(notificationService, times(1)).notify(eq(USER),
                eq(NotificationType.WORKORDER_RESOLVED), anyString(), anyString(), anyString());
    }

    @Test
    void transitionToResolved_withoutResolution_throws() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setTicketNo("WO1");
        wo.setUserId(USER);
        wo.setStatus(WorkOrderStateMachine.IN_PROGRESS);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        assertThrows(BizException.class,
                () -> service.transition(ADMIN, 1L, WorkOrderStateMachine.RESOLVED, null));
        assertThrows(BizException.class,
                () -> service.transition(ADMIN, 1L, WorkOrderStateMachine.RESOLVED, "  "));
    }

    @Test
    void closeByUser_setsClosedAndSatisfaction() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setTicketNo("WO1");
        wo.setUserId(USER);
        wo.setAssigneeId(ASSIGNEE);
        wo.setStatus(WorkOrderStateMachine.RESOLVED);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

        service.closeByUser(USER, 1L, 5);

        ArgumentCaptor<WorkOrder> cap3 = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper, times(1)).updateById(cap3.capture());
        WorkOrder saved3 = cap3.getValue();
        assertEquals(WorkOrderStateMachine.CLOSED, saved3.getStatus());
        assertEquals(Integer.valueOf(5), saved3.getSatisfaction());
        assertNotNull(saved3.getClosedAt());
        verify(eventPublisher, times(1)).publish(eq(EventType.WORKORDER_CLOSED), anyString(), any());
        verify(notificationService, times(1)).notify(eq(ASSIGNEE),
                eq(NotificationType.WORKORDER_CLOSED), anyString(), anyString(), anyString());
    }

    @Test
    void closeByOtherUser_throwsForbidden() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setUserId(USER);
        wo.setStatus(WorkOrderStateMachine.RESOLVED);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        assertThrows(BizException.class, () -> service.closeByUser(2L, 1L, 5));
    }

    @Test
    void closeByUser_whenAlreadyClosed_throws() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setUserId(USER);
        wo.setStatus(WorkOrderStateMachine.CLOSED);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);

        assertThrows(BizException.class, () -> service.closeByUser(USER, 1L, 5));
    }
}
