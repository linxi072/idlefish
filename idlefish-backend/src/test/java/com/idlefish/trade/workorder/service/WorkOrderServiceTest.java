package com.idlefish.trade.workorder.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.dispute.DisputeStateMachine;
import com.idlefish.trade.dispute.entity.Dispute;
import com.idlefish.trade.dispute.mapper.DisputeMapper;
import com.idlefish.trade.dispute.service.DisputeService;
import com.idlefish.trade.event.DomainEventPublisher;
import com.idlefish.trade.event.EventType;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.workorder.WorkOrderStateMachine;
import com.idlefish.trade.workorder.dto.WorkOrderLinkedDisputeVO;
import com.idlefish.trade.workorder.entity.WorkOrder;
import com.idlefish.trade.workorder.mapper.WorkOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客服工单服务单测：聚焦 create / 维权转人工 / assign / transition / closeByUser 与领域事件、通知、SLA 接线。
 *
 * <p>采用纯 Mockito 离线范式；通知为 best-effort（内部 try/catch），无需对其打桩。
 * 涉及 MyBatis-Plus Lambda 条件构造器的用例需先初始化 TableInfo（否则取不到 lambda 列缓存）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderServiceTest {

    private static final Long USER = 1L;
    private static final Long ADMIN = 99L;
    private static final Long ASSIGNEE = 88L;

    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private DisputeMapper disputeMapper;
    @Mock private NotificationService notificationService;
    @Mock private MetricsRegistry metrics;
    @Mock private DomainEventPublisher eventPublisher;

    @InjectMocks
    private WorkOrderService service;

    @BeforeAll
    static void initMpLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "dummy");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, WorkOrder.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Dispute.class);
    }

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

    // ==================== F-12 补完：维权纠纷联通 ====================

    @Test
    void createFromDispute_derivesTypePriorityAndLinksDispute() {
        when(disputeMapper.selectOne(any())).thenReturn(
                dispute(DisputeStateMachine.PENDING, USER, 2L, DisputeService.TYPE_REFUND_REJECTED, 150_000L, "ORD1"));
        when(workOrderMapper.selectCount(any())).thenReturn(0L);
        when(workOrderMapper.insert(any(WorkOrder.class))).thenAnswer(inv -> {
            ((WorkOrder) inv.getArgument(0)).setId(7L);
            return 1;
        });

        Long id = service.createFromDispute(USER, "DP1", WorkOrderService.CHANNEL_MINI, null);

        assertEquals(7L, id.longValue());
        ArgumentCaptor<WorkOrder> cap = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper, times(1)).insert(cap.capture());
        WorkOrder saved = cap.getValue();
        // 争议类型 REFUND_REJECTED → 工单类型 REFUND；金额 ≥1000 元 → URGENT
        assertEquals(WorkOrderService.TYPE_REFUND, saved.getType());
        assertEquals(WorkOrderService.PRIORITY_URGENT, saved.getPriority());
        assertEquals("DP1", saved.getDisputeNo());
        assertEquals("ORD1", saved.getOrderNo());
        assertEquals(WorkOrderStateMachine.OPEN, saved.getStatus());
        assertNotNull(saved.getFirstResponseDeadline());
        assertNotNull(saved.getResolveDeadline());
        assertEquals(Integer.valueOf(0), saved.getSlaBreached());
        verify(eventPublisher, times(1)).publish(eq(EventType.WORKORDER_OPENED), anyString(), any());
    }

    @Test
    void createFromDispute_platformIntervention_escalatesPriority() {
        when(disputeMapper.selectOne(any())).thenReturn(
                dispute(DisputeStateMachine.PLATFORM, USER, 2L, DisputeService.TYPE_NOT_AS_DESC, 1_000L, "ORD1"));
        when(workOrderMapper.selectCount(any())).thenReturn(0L);
        when(workOrderMapper.insert(any(WorkOrder.class))).thenAnswer(inv -> {
            ((WorkOrder) inv.getArgument(0)).setId(8L);
            return 1;
        });

        service.createFromDispute(USER, "DP2", WorkOrderService.CHANNEL_APP, null);

        ArgumentCaptor<WorkOrder> cap = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper, times(1)).insert(cap.capture());
        // 平台已介入：金额仅 10 元本应 LOW，但因 PLATFORM 抬升至 HIGH；描述不符 → COMPLAINT
        assertEquals(WorkOrderService.TYPE_COMPLAINT, cap.getValue().getType());
        assertEquals(WorkOrderService.PRIORITY_HIGH, cap.getValue().getPriority());
    }

    @Test
    void createFromDispute_disputeNotFound_throws() {
        when(disputeMapper.selectOne(any())).thenReturn(null);
        assertThrows(BizException.class,
                () -> service.createFromDispute(USER, "DP404", WorkOrderService.CHANNEL_APP, null));
    }

    @Test
    void createFromDispute_notParty_throwsForbidden() {
        when(disputeMapper.selectOne(any())).thenReturn(
                dispute(DisputeStateMachine.PENDING, 1L, 2L, DisputeService.TYPE_DAMAGED, 5_000L, "ORD1"));
        BizException ex = assertThrows(BizException.class,
                () -> service.createFromDispute(3L, "DP1", WorkOrderService.CHANNEL_APP, null));
        assertEquals(com.idlefish.trade.common.Code.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void createFromDispute_terminalDispute_throws() {
        when(disputeMapper.selectOne(any())).thenReturn(
                dispute(DisputeStateMachine.RESOLVED, USER, 2L, DisputeService.TYPE_DAMAGED, 5_000L, "ORD1"));
        assertThrows(BizException.class,
                () -> service.createFromDispute(USER, "DP1", WorkOrderService.CHANNEL_APP, null));
    }

    @Test
    void createFromDispute_duplicateOpenWorkOrder_throws() {
        when(disputeMapper.selectOne(any())).thenReturn(
                dispute(DisputeStateMachine.PENDING, USER, 2L, DisputeService.TYPE_DAMAGED, 5_000L, "ORD1"));
        // 同一维权单已存在未结工单 → 幂等拦截
        when(workOrderMapper.selectCount(any())).thenReturn(1L);
        BizException ex = assertThrows(BizException.class,
                () -> service.createFromDispute(USER, "DP1", WorkOrderService.CHANNEL_APP, null));
        assertEquals(com.idlefish.trade.common.Code.WORKORDER_DISPUTE_LINKED.getCode(), ex.getCode());
    }

    @Test
    void createFromDispute_invalidChannel_throws() {
        assertThrows(BizException.class, () -> service.createFromDispute(USER, "DP1", "BOGUS", null));
    }

    @Test
    void userLinkedDispute_returnsSummary() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setUserId(USER);
        wo.setDisputeNo("DP1");
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(disputeMapper.selectOne(any())).thenReturn(
                dispute(DisputeStateMachine.PLATFORM, USER, 2L, DisputeService.TYPE_DAMAGED, 8_000L, "ORD1"));

        WorkOrderLinkedDisputeVO vo = service.userLinkedDispute(USER, 1L);
        assertEquals("DP1", vo.disputeNo());
        assertEquals(DisputeStateMachine.PLATFORM, vo.status());
        assertEquals(Long.valueOf(8_000L), vo.amount());
    }

    @Test
    void userLinkedDispute_otherUser_throwsForbidden() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setUserId(USER);
        wo.setDisputeNo("DP1");
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        assertThrows(BizException.class, () -> service.userLinkedDispute(2L, 1L));
    }

    @Test
    void userLinkedDispute_withoutLink_returnsEmpty() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setUserId(USER);
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        assertNull(service.userLinkedDispute(USER, 1L).disputeNo());
    }

    // ==================== F-12 补完：时效 SLA ====================

    @Test
    void create_setsSlaDeadlinesByPriority() {
        when(workOrderMapper.insert(any(WorkOrder.class))).thenAnswer(inv -> {
            ((WorkOrder) inv.getArgument(0)).setId(1L);
            return 1;
        });
        service.create(USER, WorkOrderService.TYPE_GENERAL, WorkOrderService.PRIORITY_URGENT,
                WorkOrderService.CHANNEL_APP, "标题", "描述", null);
        ArgumentCaptor<WorkOrder> cap = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper, times(1)).insert(cap.capture());
        WorkOrder saved = cap.getValue();
        // URGENT：首响 15 分钟 / 解决 120 分钟，两者均以建单时刻起算且解决阈值更宽松
        assertNotNull(saved.getFirstResponseDeadline());
        assertNotNull(saved.getResolveDeadline());
        assertTrue(saved.getResolveDeadline().isAfter(saved.getFirstResponseDeadline()),
                "解决到期时间应晚于首响到期时间");
        assertTrue(saved.getFirstResponseDeadline().isAfter(LocalDateTime.now().plusMinutes(14)),
                "URGENT 首响应为 15 分钟");
        assertEquals(Integer.valueOf(0), saved.getSlaBreached());
    }

    @Test
    void assign_lateFirstResponse_marksSlaBreached() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setTicketNo("WO1");
        wo.setUserId(USER);
        wo.setStatus(WorkOrderStateMachine.OPEN);
        wo.setPriority(WorkOrderService.PRIORITY_URGENT);
        wo.setCreatedAt(LocalDateTime.now().minusHours(2)); // 远超 URGENT 首响 15 分钟
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

        service.assign(ADMIN, 1L, ASSIGNEE);

        ArgumentCaptor<WorkOrder> cap = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper, times(1)).updateById(cap.capture());
        assertEquals(Integer.valueOf(1), cap.getValue().getSlaBreached());
        verify(metrics, times(1)).increment("workorder.sla.breached");
    }

    @Test
    void assign_inTime_keepsSlaOk() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setTicketNo("WO1");
        wo.setUserId(USER);
        wo.setStatus(WorkOrderStateMachine.OPEN);
        wo.setPriority(WorkOrderService.PRIORITY_NORMAL);
        wo.setCreatedAt(LocalDateTime.now().minusMinutes(5)); // NORMAL 首响 120 分钟内
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

        service.assign(ADMIN, 1L, ASSIGNEE);

        ArgumentCaptor<WorkOrder> cap = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper, times(1)).updateById(cap.capture());
        assertNull(cap.getValue().getSlaBreached());
    }

    @Test
    void transitionToResolved_lateResolve_marksSlaBreached() {
        WorkOrder wo = new WorkOrder();
        wo.setId(1L);
        wo.setTicketNo("WO1");
        wo.setUserId(USER);
        wo.setStatus(WorkOrderStateMachine.IN_PROGRESS);
        wo.setPriority(WorkOrderService.PRIORITY_URGENT);
        wo.setCreatedAt(LocalDateTime.now().minusHours(5)); // 远超 URGENT 解决 120 分钟
        when(workOrderMapper.selectById(1L)).thenReturn(wo);
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

        service.transition(ADMIN, 1L, WorkOrderStateMachine.RESOLVED, "已处理");

        ArgumentCaptor<WorkOrder> cap = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper, times(1)).updateById(cap.capture());
        assertEquals(Integer.valueOf(1), cap.getValue().getSlaBreached());
        verify(metrics, times(1)).increment("workorder.sla.breached");
    }

    private Dispute dispute(String status, Long buyerId, Long sellerId, String type, Long amount, String orderNo) {
        Dispute d = new Dispute();
        d.setId(5L);
        d.setDisputeNo("DP1");
        d.setOrderNo(orderNo);
        d.setBuyerId(buyerId);
        d.setSellerId(sellerId);
        d.setType(type);
        d.setExpectation(DisputeService.EXPECT_REFUND);
        d.setAmount(amount);
        d.setStatus(status);
        return d;
    }
}
