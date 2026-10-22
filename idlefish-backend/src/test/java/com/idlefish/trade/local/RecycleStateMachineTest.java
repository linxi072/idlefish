package com.idlefish.trade.local;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回收单状态机纯函数单测（ITER-F05 / REQ-03、REQ-05、REQ-09）。
 */
class RecycleStateMachineTest {

    @Test
    @DisplayName("主流程全链路允许流转")
    void happyPathTransitions() {
        assertTrue(RecycleStateMachine.canTransition(RecycleStateMachine.PENDING_PICKUP, RecycleStateMachine.PICKED_UP));
        assertTrue(RecycleStateMachine.canTransition(RecycleStateMachine.PICKED_UP, RecycleStateMachine.INSPECTING));
        assertTrue(RecycleStateMachine.canTransition(RecycleStateMachine.INSPECTING, RecycleStateMachine.QUOTED));
        assertTrue(RecycleStateMachine.canTransition(RecycleStateMachine.QUOTED, RecycleStateMachine.CONFIRMED));
        assertTrue(RecycleStateMachine.canTransition(RecycleStateMachine.CONFIRMED, RecycleStateMachine.PAID));
    }

    @Test
    @DisplayName("REQ-03 仅待上门阶段可无费用取消")
    void cancelOnlyBeforePickup() {
        assertTrue(RecycleStateMachine.canUserCancel(RecycleStateMachine.PENDING_PICKUP));
        assertFalse(RecycleStateMachine.canUserCancel(RecycleStateMachine.PICKED_UP));
        assertFalse(RecycleStateMachine.canUserCancel(RecycleStateMachine.INSPECTING));
        assertFalse(RecycleStateMachine.canUserCancel(RecycleStateMachine.QUOTED));
        assertFalse(RecycleStateMachine.canUserCancel(RecycleStateMachine.CANCELED));
    }

    @Test
    @DisplayName("REQ-05 用户拒绝报价必须可流转为 REJECTED，且绝不可自动成交")
    void quoteRejectionAlwaysAllowed() {
        assertTrue(RecycleStateMachine.canTransition(RecycleStateMachine.QUOTED, RecycleStateMachine.REJECTED));
        // QUOTED 之后没有任何路径绕过用户确认直接放款
        assertFalse(RecycleStateMachine.canTransition(RecycleStateMachine.QUOTED, RecycleStateMachine.PAID));
        // 用户确认是放款的唯一前置
        assertFalse(RecycleStateMachine.canPay(RecycleStateMachine.QUOTED));
        assertTrue(RecycleStateMachine.canPay(RecycleStateMachine.CONFIRMED));
    }

    @Test
    @DisplayName("REQ-05 拒绝后不得再放款/再确认（终态）")
    void terminalCannotFlowFurther() {
        assertFalse(RecycleStateMachine.canTransition(RecycleStateMachine.REJECTED, RecycleStateMachine.PAID));
        assertFalse(RecycleStateMachine.canTransition(RecycleStateMachine.CANCELED, RecycleStateMachine.PAID));
        assertFalse(RecycleStateMachine.canTransition(RecycleStateMachine.PAID, RecycleStateMachine.REJECTED));
        assertTrue(RecycleStateMachine.isTerminal(RecycleStateMachine.REJECTED));
        assertTrue(RecycleStateMachine.isTerminal(RecycleStateMachine.PAID));
    }

    @Test
    @DisplayName("REQ-09 任一非终态都可显式收敛为 UNAVAILABLE，禁止静默卡住")
    void unavailableFromAnyOpenStage() {
        for (String s : new String[]{
                RecycleStateMachine.PENDING_PICKUP, RecycleStateMachine.PICKED_UP,
                RecycleStateMachine.INSPECTING, RecycleStateMachine.QUOTED, RecycleStateMachine.CONFIRMED}) {
            assertTrue(RecycleStateMachine.canTransition(s, RecycleStateMachine.UNAVAILABLE),
                    s + " 应可置为不可用");
            assertTrue(RecycleStateMachine.isOpen(s));
        }
        assertTrue(RecycleStateMachine.isTerminal(RecycleStateMachine.UNAVAILABLE));
    }

    @Test
    @DisplayName("非法来源 / 目标一律拒绝")
    void invalidTransitions() {
        assertFalse(RecycleStateMachine.canTransition(null, RecycleStateMachine.PICKED_UP));
        assertFalse(RecycleStateMachine.canTransition(RecycleStateMachine.PENDING_PICKUP, null));
        assertFalse(RecycleStateMachine.canTransition("NOT_A_STATUS", RecycleStateMachine.PICKED_UP));
        assertFalse(RecycleStateMachine.canTransition(RecycleStateMachine.PENDING_PICKUP, RecycleStateMachine.PAID));
        // 待上门阶段不能跳过质检直接出报价
        assertFalse(RecycleStateMachine.canTransition(RecycleStateMachine.PENDING_PICKUP, RecycleStateMachine.QUOTED));
    }

    @Test
    @DisplayName("canQuote / canRespondQuote 的阶段约束")
    void quoteAndRespondGuards() {
        assertFalse(RecycleStateMachine.canQuote(RecycleStateMachine.PENDING_PICKUP));
        assertTrue(RecycleStateMachine.canQuote(RecycleStateMachine.PICKED_UP));
        assertTrue(RecycleStateMachine.canQuote(RecycleStateMachine.INSPECTING));
        assertFalse(RecycleStateMachine.canQuote(RecycleStateMachine.QUOTED));
        assertTrue(RecycleStateMachine.canRespondQuote(RecycleStateMachine.QUOTED));
        assertFalse(RecycleStateMachine.canRespondQuote(RecycleStateMachine.CONFIRMED));
    }
}
