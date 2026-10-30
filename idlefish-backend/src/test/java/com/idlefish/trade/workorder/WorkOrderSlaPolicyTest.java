package com.idlefish.trade.workorder;

import com.idlefish.trade.workorder.service.WorkOrderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客服工单时效 SLA 策略纯函数单测（F-12 补完）：阈值、到期时间、超时判定与达标率。
 */
class WorkOrderSlaPolicyTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 28, 10, 0);

    @Test
    void thresholds_byPriority() {
        assertEquals(15L, WorkOrderSlaPolicy.firstResponseMinutes(WorkOrderService.PRIORITY_URGENT));
        assertEquals(30L, WorkOrderSlaPolicy.firstResponseMinutes(WorkOrderService.PRIORITY_HIGH));
        assertEquals(120L, WorkOrderSlaPolicy.firstResponseMinutes(WorkOrderService.PRIORITY_NORMAL));
        assertEquals(480L, WorkOrderSlaPolicy.firstResponseMinutes(WorkOrderService.PRIORITY_LOW));
        assertEquals(120L, WorkOrderSlaPolicy.resolveMinutes(WorkOrderService.PRIORITY_URGENT));
        assertEquals(4320L, WorkOrderSlaPolicy.resolveMinutes(WorkOrderService.PRIORITY_LOW));
        // 未知优先级兜底 NORMAL
        assertEquals(120L, WorkOrderSlaPolicy.firstResponseMinutes("BOGUS"));
        assertEquals(1440L, WorkOrderSlaPolicy.resolveMinutes(null));
    }

    @Test
    void deadlines_startFromCreatedAt() {
        assertEquals(T0.plusMinutes(15), WorkOrderSlaPolicy.firstResponseDeadline(T0, WorkOrderService.PRIORITY_URGENT));
        assertEquals(T0.plusMinutes(120), WorkOrderSlaPolicy.resolveDeadline(T0, WorkOrderService.PRIORITY_URGENT));
        assertNull(WorkOrderSlaPolicy.firstResponseDeadline(null, WorkOrderService.PRIORITY_URGENT));
        assertNull(WorkOrderSlaPolicy.resolveDeadline(null, WorkOrderService.PRIORITY_URGENT));
    }

    @Test
    void firstResponseBreached_judgedByResponseTimeOrNow() {
        // 已响应：以响应时间对比（URGENT 15 分钟）
        assertTrue(WorkOrderSlaPolicy.firstResponseBreached(T0, T0.plusMinutes(20),
                WorkOrderService.PRIORITY_URGENT, T0.plusMinutes(30)));
        assertFalse(WorkOrderSlaPolicy.firstResponseBreached(T0, T0.plusMinutes(10),
                WorkOrderService.PRIORITY_URGENT, T0.plusMinutes(30)));
        // 未响应：以当前时间对比
        assertTrue(WorkOrderSlaPolicy.firstResponseBreached(T0, null,
                WorkOrderService.PRIORITY_URGENT, T0.plusMinutes(16)));
        assertFalse(WorkOrderSlaPolicy.firstResponseBreached(T0, null,
                WorkOrderService.PRIORITY_URGENT, T0.plusMinutes(5)));
        // 无起算点不判定
        assertFalse(WorkOrderSlaPolicy.firstResponseBreached(null, null,
                WorkOrderService.PRIORITY_URGENT, T0));
    }

    @Test
    void resolveBreached_openWorkOrderUsesNow() {
        // 已解决但超时
        assertTrue(WorkOrderSlaPolicy.resolveBreached(T0, T0.plusMinutes(200),
                WorkOrderService.PRIORITY_URGENT, T0.plusMinutes(200)));
        // 未解决且当前已超期（扫描口径）
        assertTrue(WorkOrderSlaPolicy.resolveBreached(T0, null,
                WorkOrderService.PRIORITY_URGENT, T0.plusMinutes(121)));
        // 未解决但仍在时效内
        assertFalse(WorkOrderSlaPolicy.resolveBreached(T0, null,
                WorkOrderService.PRIORITY_URGENT, T0.plusMinutes(60)));
    }

    @Test
    void breached_isUnionOfFirstResponseAndResolve() {
        // URGENT：首响 1 分钟达标，但 500 分钟仍未解决（解决阈值 120 分钟）→ 整体判超时
        assertTrue(WorkOrderSlaPolicy.breached(T0, T0.plusMinutes(1), null,
                WorkOrderService.PRIORITY_URGENT, T0.plusMinutes(500)));
        assertFalse(WorkOrderSlaPolicy.breached(T0, T0.plusMinutes(1), T0.plusMinutes(2),
                WorkOrderService.PRIORITY_LOW, T0.plusMinutes(5)));
    }

    @Test
    void complianceRate_handlesEmptyAndClamps() {
        assertEquals(1.0, WorkOrderSlaPolicy.complianceRate(0, 0), 0.0001);
        assertEquals(0.7, WorkOrderSlaPolicy.complianceRate(10, 3), 0.0001);
        // 超时数大于总数（脏数据）时钳制，不出现负达标率
        assertEquals(0.0, WorkOrderSlaPolicy.complianceRate(5, 9), 0.0001);
    }
}
