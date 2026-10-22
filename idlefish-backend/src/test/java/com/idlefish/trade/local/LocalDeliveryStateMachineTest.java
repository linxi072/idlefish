package com.idlefish.trade.local;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同城配送状态机纯函数单测（ITER-F05 / REQ-06、REQ-09）。
 */
class LocalDeliveryStateMachineTest {

    @Test
    @DisplayName("主流程：CREATED → ACCEPTED → DELIVERING → ARRIVED → SIGNED")
    void happyPath() {
        assertTrue(LocalDeliveryStateMachine.canTransition(LocalDeliveryStateMachine.CREATED, LocalDeliveryStateMachine.ACCEPTED));
        assertTrue(LocalDeliveryStateMachine.canTransition(LocalDeliveryStateMachine.ACCEPTED, LocalDeliveryStateMachine.DELIVERING));
        assertTrue(LocalDeliveryStateMachine.canTransition(LocalDeliveryStateMachine.DELIVERING, LocalDeliveryStateMachine.ARRIVED));
        assertTrue(LocalDeliveryStateMachine.canTransition(LocalDeliveryStateMachine.ARRIVED, LocalDeliveryStateMachine.SIGNED));
    }

    @Test
    @DisplayName("REQ-09 配送中任一未送达阶段都可显式置为 FAILED（服务商不可用不得静默卡住）")
    void failFromAnyOpenStage() {
        // 送达后只能签收，不提供「送达后又失败」的语义
        for (String s : new String[]{
                LocalDeliveryStateMachine.CREATED, LocalDeliveryStateMachine.ACCEPTED,
                LocalDeliveryStateMachine.DELIVERING}) {
            assertTrue(LocalDeliveryStateMachine.canTransition(s, LocalDeliveryStateMachine.FAILED), s);
        }
        assertFalse(LocalDeliveryStateMachine.canTransition(LocalDeliveryStateMachine.ARRIVED, LocalDeliveryStateMachine.FAILED));
    }

    @Test
    @DisplayName("送达前可取消，签收后不可再取消/流转")
    void cancelAndTerminal() {
        assertTrue(LocalDeliveryStateMachine.canCancel(LocalDeliveryStateMachine.CREATED));
        assertTrue(LocalDeliveryStateMachine.canCancel(LocalDeliveryStateMachine.DELIVERING));
        assertFalse(LocalDeliveryStateMachine.canCancel(LocalDeliveryStateMachine.SIGNED));
        assertFalse(LocalDeliveryStateMachine.canTransition(LocalDeliveryStateMachine.SIGNED, LocalDeliveryStateMachine.CANCELED));
        assertFalse(LocalDeliveryStateMachine.canTransition(LocalDeliveryStateMachine.FAILED, LocalDeliveryStateMachine.SIGNED));
    }

    @Test
    @DisplayName("非法流转拒绝：创建后不能直接签收 / 送达后不能再回到配送中")
    void invalidTransitions() {
        assertFalse(LocalDeliveryStateMachine.canTransition(LocalDeliveryStateMachine.CREATED, LocalDeliveryStateMachine.SIGNED));
        assertFalse(LocalDeliveryStateMachine.canTransition(LocalDeliveryStateMachine.ARRIVED, LocalDeliveryStateMachine.DELIVERING));
        assertFalse(LocalDeliveryStateMachine.canTransition(null, LocalDeliveryStateMachine.SIGNED));
    }

    @Test
    @DisplayName("签收后仍可读轨迹（历史查询）")
    void trackAllowedAfterSign() {
        assertTrue(LocalDeliveryStateMachine.canTrack(LocalDeliveryStateMachine.SIGNED));
        assertTrue(LocalDeliveryStateMachine.canTrack(LocalDeliveryStateMachine.DELIVERING));
        assertFalse(LocalDeliveryStateMachine.canTrack(LocalDeliveryStateMachine.FAILED));
    }
}
