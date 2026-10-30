package com.idlefish.trade.workorder;

import com.idlefish.trade.dispute.DisputeStateMachine;
import com.idlefish.trade.dispute.service.DisputeService;
import com.idlefish.trade.workorder.service.WorkOrderService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 维权纠纷 → 客服工单映射纯函数单测（F-12 补完）：可转人工判定、类型/优先级推导与文案生成。
 */
class WorkOrderDisputeLinkTest {

    @Test
    void canConvert_onlyOpenDispute() {
        assertTrue(WorkOrderDisputeLink.canConvert(DisputeStateMachine.PENDING));
        assertTrue(WorkOrderDisputeLink.canConvert(DisputeStateMachine.SELLER_REPLIED));
        assertTrue(WorkOrderDisputeLink.canConvert(DisputeStateMachine.PLATFORM));
        assertFalse(WorkOrderDisputeLink.canConvert(DisputeStateMachine.RESOLVED));
        assertFalse(WorkOrderDisputeLink.canConvert(DisputeStateMachine.CLOSED));
        assertFalse(WorkOrderDisputeLink.canConvert(DisputeStateMachine.CANCELED));
        assertFalse(WorkOrderDisputeLink.canConvert(null));
    }

    @Test
    void typeOf_mapsDisputeType() {
        assertEquals(WorkOrderService.TYPE_REFUND, WorkOrderDisputeLink.typeOf(DisputeService.TYPE_REFUND_REJECTED));
        assertEquals(WorkOrderService.TYPE_REFUND, WorkOrderDisputeLink.typeOf(DisputeService.TYPE_NOT_RECEIVED));
        assertEquals(WorkOrderService.TYPE_REFUND, WorkOrderDisputeLink.typeOf(DisputeService.TYPE_DAMAGED));
        assertEquals(WorkOrderService.TYPE_COMPLAINT, WorkOrderDisputeLink.typeOf(DisputeService.TYPE_NOT_AS_DESC));
        assertEquals(WorkOrderService.TYPE_GENERAL, WorkOrderDisputeLink.typeOf("UNKNOWN"));
    }

    @Test
    void priorityOf_byAmountTier() {
        assertEquals(WorkOrderService.PRIORITY_URGENT,
                WorkOrderDisputeLink.priorityOf(100_000L, DisputeStateMachine.PENDING));
        assertEquals(WorkOrderService.PRIORITY_HIGH,
                WorkOrderDisputeLink.priorityOf(20_000L, DisputeStateMachine.PENDING));
        assertEquals(WorkOrderService.PRIORITY_NORMAL,
                WorkOrderDisputeLink.priorityOf(5_000L, DisputeStateMachine.PENDING));
        assertEquals(WorkOrderService.PRIORITY_LOW,
                WorkOrderDisputeLink.priorityOf(1_000L, DisputeStateMachine.PENDING));
        assertEquals(WorkOrderService.PRIORITY_LOW,
                WorkOrderDisputeLink.priorityOf(null, DisputeStateMachine.PENDING));
    }

    @Test
    void priorityOf_platformInterventionEscalates() {
        // 平台介入：小额也从 LOW/NORMAL 抬升到 HIGH
        assertEquals(WorkOrderService.PRIORITY_HIGH,
                WorkOrderDisputeLink.priorityOf(1_000L, DisputeStateMachine.PLATFORM));
        assertEquals(WorkOrderService.PRIORITY_HIGH,
                WorkOrderDisputeLink.priorityOf(30_000L, DisputeStateMachine.PLATFORM));
        // 已是 URGENT 时保持不变
        assertEquals(WorkOrderService.PRIORITY_URGENT,
                WorkOrderDisputeLink.priorityOf(500_000L, DisputeStateMachine.PLATFORM));
        // 已裁决状态不抬升（仅未结维权单可转人工，此处验证状态不影响金额分档）
        assertEquals(WorkOrderService.PRIORITY_LOW,
                WorkOrderDisputeLink.priorityOf(1_000L, DisputeStateMachine.RESOLVED));
    }

    @Test
    void subjectAndDescription_readable() {
        String subject = WorkOrderDisputeLink.subjectOf("DP1", DisputeService.TYPE_NOT_AS_DESC);
        assertTrue(subject.contains("DP1"));
        assertTrue(subject.contains("描述不符"));

        String desc = WorkOrderDisputeLink.defaultDescription(DisputeService.TYPE_DAMAGED,
                DisputeService.EXPECT_RETURN_REFUND);
        assertTrue(desc.contains("退货退款"));
        assertTrue(desc.contains(DisputeService.TYPE_DAMAGED));
        assertFalse(desc.isBlank());
    }
}
