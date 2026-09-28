package com.idlefish.trade.dispute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-17 维权工单状态机纯函数离线单测（无 Spring / 无 MySQL）。
 */
class DisputeStateMachineTest {

    @Test
    void isOpen() {
        assertTrue(DisputeStateMachine.isOpen(DisputeStateMachine.PENDING));
        assertTrue(DisputeStateMachine.isOpen(DisputeStateMachine.SELLER_REPLIED));
        assertTrue(DisputeStateMachine.isOpen(DisputeStateMachine.PLATFORM));
        assertFalse(DisputeStateMachine.isOpen(DisputeStateMachine.RESOLVED));
        assertFalse(DisputeStateMachine.isOpen(DisputeStateMachine.CANCELED));
        assertFalse(DisputeStateMachine.isOpen(DisputeStateMachine.CLOSED));
        assertFalse(DisputeStateMachine.isOpen(null));
    }

    @Test
    void isTerminal() {
        assertTrue(DisputeStateMachine.isTerminal(DisputeStateMachine.RESOLVED));
        assertTrue(DisputeStateMachine.isTerminal(DisputeStateMachine.CANCELED));
        assertTrue(DisputeStateMachine.isTerminal(DisputeStateMachine.CLOSED));
        assertFalse(DisputeStateMachine.isTerminal(DisputeStateMachine.PENDING));
        assertFalse(DisputeStateMachine.isTerminal(DisputeStateMachine.PLATFORM));
        assertFalse(DisputeStateMachine.isTerminal(null));
    }

    @Test
    void canTransitionFromPending() {
        // 待处理：可举证 / 申请平台介入 / 撤销 / 平台直接裁决
        assertTrue(DisputeStateMachine.canTransition(DisputeStateMachine.PENDING, DisputeStateMachine.SELLER_REPLIED));
        assertTrue(DisputeStateMachine.canTransition(DisputeStateMachine.PENDING, DisputeStateMachine.PLATFORM));
        assertTrue(DisputeStateMachine.canTransition(DisputeStateMachine.PENDING, DisputeStateMachine.CANCELED));
        assertTrue(DisputeStateMachine.canTransition(DisputeStateMachine.PENDING, DisputeStateMachine.RESOLVED));
        // 不可直接归档
        assertFalse(DisputeStateMachine.canTransition(DisputeStateMachine.PENDING, DisputeStateMachine.CLOSED));
    }

    @Test
    void canTransitionFromSellerRepliedAndPlatform() {
        assertTrue(DisputeStateMachine.canTransition(DisputeStateMachine.SELLER_REPLIED, DisputeStateMachine.PLATFORM));
        assertTrue(DisputeStateMachine.canTransition(DisputeStateMachine.SELLER_REPLIED, DisputeStateMachine.RESOLVED));
        assertTrue(DisputeStateMachine.canTransition(DisputeStateMachine.SELLER_REPLIED, DisputeStateMachine.CANCELED));
        // 已举证不可回退为待处理
        assertFalse(DisputeStateMachine.canTransition(DisputeStateMachine.SELLER_REPLIED, DisputeStateMachine.PENDING));

        // 平台介入中仅可裁决
        assertTrue(DisputeStateMachine.canTransition(DisputeStateMachine.PLATFORM, DisputeStateMachine.RESOLVED));
        assertFalse(DisputeStateMachine.canTransition(DisputeStateMachine.PLATFORM, DisputeStateMachine.PENDING));
        assertFalse(DisputeStateMachine.canTransition(DisputeStateMachine.PLATFORM, DisputeStateMachine.CANCELED));
    }

    @Test
    void canTransitionTerminal() {
        // 已裁决 / 已撤销可归档；归档为终态不可逆
        assertTrue(DisputeStateMachine.canTransition(DisputeStateMachine.RESOLVED, DisputeStateMachine.CLOSED));
        assertTrue(DisputeStateMachine.canTransition(DisputeStateMachine.CANCELED, DisputeStateMachine.CLOSED));
        assertFalse(DisputeStateMachine.canTransition(DisputeStateMachine.CLOSED, DisputeStateMachine.RESOLVED));
        assertFalse(DisputeStateMachine.canTransition(DisputeStateMachine.RESOLVED, DisputeStateMachine.PENDING));
        // null 边界
        assertFalse(DisputeStateMachine.canTransition(null, DisputeStateMachine.RESOLVED));
        assertFalse(DisputeStateMachine.canTransition(DisputeStateMachine.PENDING, null));
    }

    @Test
    void operationGuards() {
        // 买家撤销：平台介入后不可撤销
        assertTrue(DisputeStateMachine.canBuyerCancel(DisputeStateMachine.PENDING));
        assertTrue(DisputeStateMachine.canBuyerCancel(DisputeStateMachine.SELLER_REPLIED));
        assertFalse(DisputeStateMachine.canBuyerCancel(DisputeStateMachine.PLATFORM));

        // 申请平台介入
        assertTrue(DisputeStateMachine.canApplyPlatform(DisputeStateMachine.PENDING));
        assertTrue(DisputeStateMachine.canApplyPlatform(DisputeStateMachine.SELLER_REPLIED));
        assertFalse(DisputeStateMachine.canApplyPlatform(DisputeStateMachine.PLATFORM));

        // 平台裁决
        assertTrue(DisputeStateMachine.canResolve(DisputeStateMachine.PENDING));
        assertTrue(DisputeStateMachine.canResolve(DisputeStateMachine.SELLER_REPLIED));
        assertTrue(DisputeStateMachine.canResolve(DisputeStateMachine.PLATFORM));
        assertFalse(DisputeStateMachine.canResolve(DisputeStateMachine.RESOLVED));
        assertFalse(DisputeStateMachine.canResolve(DisputeStateMachine.CLOSED));
    }
}
