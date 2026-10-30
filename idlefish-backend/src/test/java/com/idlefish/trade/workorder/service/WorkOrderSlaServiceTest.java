package com.idlefish.trade.workorder.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.workorder.WorkOrderStateMachine;
import com.idlefish.trade.workorder.dto.WorkOrderSlaStatsVO;
import com.idlefish.trade.workorder.entity.WorkOrder;
import com.idlefish.trade.workorder.mapper.WorkOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客服工单时效 SLA 服务单测（F-12 补完）：超时扫描标记 + 触达，以及达标率统计。
 *
 * <p>纯 Mockito 离线范式；涉及 MyBatis-Plus Lambda 条件构造器，需先初始化 TableInfo。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderSlaServiceTest {

    private static final Long USER = 1L;
    private static final Long ASSIGNEE = 88L;

    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private NotificationService notificationService;
    @Mock private MetricsRegistry metrics;

    @InjectMocks
    private WorkOrderSlaService service;

    @BeforeAll
    static void initMpLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "dummy");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, WorkOrder.class);
    }

    @Test
    void scanBreached_marksAndNotifiesBothSides() {
        WorkOrder wo = workOrder(1L, "WO1", USER, ASSIGNEE, WorkOrderStateMachine.IN_PROGRESS);
        when(workOrderMapper.selectPage(any(Page.class), any())).thenReturn(pageOf(List.of(wo)));
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);

        int marked = service.scanBreached(100);

        assertEquals(1, marked);
        ArgumentCaptor<WorkOrder> cap = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper, times(1)).updateById(cap.capture());
        assertEquals(Integer.valueOf(1), cap.getValue().getSlaBreached());
        verify(metrics, times(1)).increment("workorder.sla.breached");
        // 双向触达：发起用户 + 受理客服
        verify(notificationService, times(1)).notify(eq(USER), eq(NotificationType.WORKORDER_SLA_BREACHED),
                anyString(), anyString(), anyString());
        verify(notificationService, times(1)).notify(eq(ASSIGNEE), eq(NotificationType.WORKORDER_SLA_BREACHED),
                anyString(), anyString(), anyString());
    }

    @Test
    void scanBreached_unassigned_notifiesUserOnly() {
        WorkOrder wo = workOrder(2L, "WO2", USER, null, WorkOrderStateMachine.OPEN);
        when(workOrderMapper.selectPage(any(Page.class), any())).thenReturn(pageOf(List.of(wo)));

        assertEquals(1, service.scanBreached(100));
        verify(notificationService, times(1)).notify(eq(USER), eq(NotificationType.WORKORDER_SLA_BREACHED),
                anyString(), anyString(), anyString());
    }

    @Test
    void scanBreached_noCandidate_returnsZero() {
        when(workOrderMapper.selectPage(any(Page.class), any())).thenReturn(pageOf(Collections.emptyList()));
        assertEquals(0, service.scanBreached(100));
        verify(workOrderMapper, times(0)).updateById(any(WorkOrder.class));
    }

    @Test
    void stats_computesComplianceRate() {
        // 顺序：total / breached / resolved / openBreached
        when(workOrderMapper.selectCount(any())).thenReturn(10L, 3L, 7L, 1L);

        WorkOrderSlaStatsVO vo = service.stats(7);

        assertEquals(7, vo.days());
        assertEquals(10L, vo.total());
        assertEquals(3L, vo.breached());
        assertEquals(7L, vo.resolved());
        assertEquals(1L, vo.openBreached());
        assertEquals(0.7, vo.complianceRate(), 0.0001);
    }

    @Test
    void stats_emptyWindow_defaultsToFullCompliance() {
        when(workOrderMapper.selectCount(any())).thenReturn(0L);
        WorkOrderSlaStatsVO vo = service.stats(0);
        assertEquals(7, vo.days(), "非法天数回退为默认 7 天窗口");
        assertEquals(0L, vo.total());
        assertEquals(1.0, vo.complianceRate(), 0.0001);
        assertTrue(vo.generatedAt() != null);
    }

    private Page<WorkOrder> pageOf(List<WorkOrder> records) {
        Page<WorkOrder> p = new Page<>(1, WorkOrderSlaService.DEFAULT_SCAN_LIMIT);
        p.setRecords(records);
        p.setTotal(records.size());
        return p;
    }

    private WorkOrder workOrder(Long id, String ticketNo, Long userId, Long assigneeId, String status) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setTicketNo(ticketNo);
        wo.setUserId(userId);
        wo.setAssigneeId(assigneeId);
        wo.setStatus(status);
        wo.setPriority(WorkOrderService.PRIORITY_URGENT);
        wo.setCreatedAt(LocalDateTime.now().minusHours(6));
        return wo;
    }
}
