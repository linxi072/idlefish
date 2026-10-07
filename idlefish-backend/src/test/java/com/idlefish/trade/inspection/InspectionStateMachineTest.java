package com.idlefish.trade.inspection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鉴定验货状态机纯函数单测（F-02）：覆盖状态判定、转移守卫、发货冻结窗口与超时映射。
 */
class InspectionStateMachineTest {

    @Test
    void isOpen_and_isTerminal() {
        assertTrue(InspectionStateMachine.isOpen(InspectionStateMachine.WAIT_PICKUP));
        assertTrue(InspectionStateMachine.isOpen(InspectionStateMachine.IN_TRANSIT));
        assertTrue(InspectionStateMachine.isOpen(InspectionStateMachine.INSPECTING));
        assertFalse(InspectionStateMachine.isOpen(InspectionStateMachine.PASSED));
        assertFalse(InspectionStateMachine.isOpen(InspectionStateMachine.REJECTED));

        assertTrue(InspectionStateMachine.isTerminal(InspectionStateMachine.PASSED));
        assertTrue(InspectionStateMachine.isTerminal(InspectionStateMachine.REJECTED));
        assertTrue(InspectionStateMachine.isTerminal(InspectionStateMachine.EXCEPTION));
        assertTrue(InspectionStateMachine.isTerminal(InspectionStateMachine.CANCELED));
        assertFalse(InspectionStateMachine.isTerminal(InspectionStateMachine.INSPECTING));
    }

    @Test
    void canTransition_mainFlow() {
        assertTrue(InspectionStateMachine.canTransition(InspectionStateMachine.WAIT_PICKUP, InspectionStateMachine.IN_TRANSIT));
        assertTrue(InspectionStateMachine.canTransition(InspectionStateMachine.WAIT_PICKUP, InspectionStateMachine.CANCELED));
        assertFalse(InspectionStateMachine.canTransition(InspectionStateMachine.WAIT_PICKUP, InspectionStateMachine.PASSED));

        assertTrue(InspectionStateMachine.canTransition(InspectionStateMachine.IN_TRANSIT, InspectionStateMachine.INSPECTING));
        assertTrue(InspectionStateMachine.canTransition(InspectionStateMachine.IN_TRANSIT, InspectionStateMachine.EXCEPTION));
        assertTrue(InspectionStateMachine.canTransition(InspectionStateMachine.IN_TRANSIT, InspectionStateMachine.CANCELED));

        assertTrue(InspectionStateMachine.canTransition(InspectionStateMachine.INSPECTING, InspectionStateMachine.PASSED));
        assertTrue(InspectionStateMachine.canTransition(InspectionStateMachine.INSPECTING, InspectionStateMachine.REJECTED));
        assertTrue(InspectionStateMachine.canTransition(InspectionStateMachine.INSPECTING, InspectionStateMachine.EXCEPTION));
        assertFalse(InspectionStateMachine.canTransition(InspectionStateMachine.INSPECTING, InspectionStateMachine.CANCELED));

        assertFalse(InspectionStateMachine.canTransition(InspectionStateMachine.PASSED, InspectionStateMachine.CANCELED));
    }

    @Test
    void isShippingFrozen_onlyWhenOpen() {
        assertTrue(InspectionStateMachine.isShippingFrozen(InspectionStateMachine.WAIT_PICKUP));
        assertTrue(InspectionStateMachine.isShippingFrozen(InspectionStateMachine.INSPECTING));
        assertFalse(InspectionStateMachine.isShippingFrozen(InspectionStateMachine.PASSED));
        assertFalse(InspectionStateMachine.isShippingFrozen(InspectionStateMachine.CANCELED));
    }

    @Test
    void guard_methods() {
        assertTrue(InspectionStateMachine.canReceive(InspectionStateMachine.WAIT_PICKUP));
        assertFalse(InspectionStateMachine.canReceive(InspectionStateMachine.INSPECTING));
        assertTrue(InspectionStateMachine.canStartInspect(InspectionStateMachine.IN_TRANSIT));
        assertFalse(InspectionStateMachine.canStartInspect(InspectionStateMachine.WAIT_PICKUP));
        assertTrue(InspectionStateMachine.canReceiveResult(InspectionStateMachine.INSPECTING));
        assertFalse(InspectionStateMachine.canReceiveResult(InspectionStateMachine.WAIT_PICKUP));
        assertTrue(InspectionStateMachine.canCancel(InspectionStateMachine.WAIT_PICKUP));
        assertTrue(InspectionStateMachine.canCancel(InspectionStateMachine.IN_TRANSIT));
        assertFalse(InspectionStateMachine.canCancel(InspectionStateMachine.INSPECTING));
    }

    @Test
    void timeoutTarget_mapsOpenToException_neverPass() {
        assertEquals(InspectionStateMachine.EXCEPTION, InspectionStateMachine.timeoutTarget(InspectionStateMachine.IN_TRANSIT));
        assertEquals(InspectionStateMachine.EXCEPTION, InspectionStateMachine.timeoutTarget(InspectionStateMachine.INSPECTING));
        assertNull(InspectionStateMachine.timeoutTarget(InspectionStateMachine.WAIT_PICKUP));
        assertNull(InspectionStateMachine.timeoutTarget(InspectionStateMachine.PASSED));
        assertNull(InspectionStateMachine.timeoutTarget(InspectionStateMachine.REJECTED));
    }
}
