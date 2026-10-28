package com.idlefish.trade.workorder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-12 客服工单状态机纯函数离线单测（无 Spring / 无 MySQL）。
 */
class WorkOrderStateMachineTest {

    @Test
    void isOpen() {
        assertTrue(WorkOrderStateMachine.isOpen(WorkOrderStateMachine.OPEN));
        assertTrue(WorkOrderStateMachine.isOpen(WorkOrderStateMachine.IN_PROGRESS));
        assertTrue(WorkOrderStateMachine.isOpen(WorkOrderStateMachine.WAITING_USER));
        assertFalse(WorkOrderStateMachine.isOpen(WorkOrderStateMachine.RESOLVED));
        assertFalse(WorkOrderStateMachine.isOpen(WorkOrderStateMachine.CLOSED));
        assertFalse(WorkOrderStateMachine.isOpen(null));
    }

    @Test
    void isTerminal() {
        assertTrue(WorkOrderStateMachine.isTerminal(WorkOrderStateMachine.CLOSED));
        assertFalse(WorkOrderStateMachine.isTerminal(WorkOrderStateMachine.RESOLVED));
        assertFalse(WorkOrderStateMachine.isTerminal(WorkOrderStateMachine.OPEN));
        assertFalse(WorkOrderStateMachine.isTerminal(WorkOrderStateMachine.IN_PROGRESS));
        assertFalse(WorkOrderStateMachine.isTerminal(null));
    }

    @Test
    void canTransitionFromOpen() {
        assertTrue(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.OPEN, WorkOrderStateMachine.IN_PROGRESS));
        assertTrue(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.OPEN, WorkOrderStateMachine.WAITING_USER));
        assertTrue(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.OPEN, WorkOrderStateMachine.RESOLVED));
        assertTrue(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.OPEN, WorkOrderStateMachine.CLOSED));
        // 自环不允许
        assertFalse(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.OPEN, WorkOrderStateMachine.OPEN));
    }

    @Test
    void canTransitionFromInProgressAndWaiting() {
        assertTrue(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.IN_PROGRESS, WorkOrderStateMachine.WAITING_USER));
        assertTrue(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.IN_PROGRESS, WorkOrderStateMachine.RESOLVED));
        assertTrue(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.IN_PROGRESS, WorkOrderStateMachine.CLOSED));
        assertFalse(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.IN_PROGRESS, WorkOrderStateMachine.OPEN));

        assertTrue(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.WAITING_USER, WorkOrderStateMachine.IN_PROGRESS));
        assertTrue(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.WAITING_USER, WorkOrderStateMachine.RESOLVED));
        assertTrue(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.WAITING_USER, WorkOrderStateMachine.CLOSED));
    }

    @Test
    void canTransitionResolvedAndClosed() {
        // RESOLVED 仅可流转到 CLOSED
        assertTrue(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.RESOLVED, WorkOrderStateMachine.CLOSED));
        assertFalse(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.RESOLVED, WorkOrderStateMachine.OPEN));
        // CLOSED 为终态不可逆
        assertFalse(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.CLOSED, WorkOrderStateMachine.RESOLVED));
        assertFalse(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.CLOSED, WorkOrderStateMachine.OPEN));
        // null 边界
        assertFalse(WorkOrderStateMachine.canTransition(null, WorkOrderStateMachine.CLOSED));
        assertFalse(WorkOrderStateMachine.canTransition(WorkOrderStateMachine.OPEN, null));
    }
}
