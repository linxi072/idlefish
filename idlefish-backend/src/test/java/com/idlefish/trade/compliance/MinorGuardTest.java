package com.idlefish.trade.compliance;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 未成年人保护守卫纯函数单测（ITER-U05 REQ-04/05/06）。 */
class MinorGuardTest {

    @Test
    void isMinor_and_ageBand() {
        assertTrue(MinorGuard.isMinor(8));
        assertTrue(MinorGuard.isMinor(17));
        assertFalse(MinorGuard.isMinor(18));
        // 年龄未知不得误判为未成年（避免数据缺失阻断正常交易）
        assertFalse(MinorGuard.isMinor(null));

        assertEquals("CHILD", MinorGuard.ageBand(8));
        assertEquals("TEEN", MinorGuard.ageBand(14));
        assertEquals("TEEN", MinorGuard.ageBand(17));
        assertEquals("ADULT", MinorGuard.ageBand(18));
        assertEquals("UNKNOWN", MinorGuard.ageBand(null));
    }

    @Test
    void quietHours_covers_midnight_boundary() {
        assertTrue(MinorGuard.withinQuietHours(LocalTime.of(22, 0)));
        assertTrue(MinorGuard.withinQuietHours(LocalTime.of(23, 59)));
        assertTrue(MinorGuard.withinQuietHours(LocalTime.of(0, 0)));
        assertTrue(MinorGuard.withinQuietHours(LocalTime.of(5, 59)));
        assertFalse(MinorGuard.withinQuietHours(LocalTime.of(6, 0)));
        assertFalse(MinorGuard.withinQuietHours(LocalTime.of(21, 59)));
        assertFalse(MinorGuard.withinQuietHours(null));
    }

    @Test
    void dailyLimit_default_applies_when_non_positive() {
        assertTrue(MinorGuard.exceedsDailyLimit(40, 0));
        assertTrue(MinorGuard.exceedsDailyLimit(40, -1));
        assertFalse(MinorGuard.exceedsDailyLimit(39, 40));
        assertTrue(MinorGuard.exceedsDailyLimit(60, 30));
    }

    @Test
    void adult_always_allowed() {
        assertTrue(MinorGuard.allowed(false, MinorGuard.RestrictedAction.TRADE, LocalTime.of(23, 30), 999, 40));
        assertNull(MinorGuard.denyReason(false, MinorGuard.RestrictedAction.PAY, LocalTime.of(23, 30), 999, 40));
    }

    @Test
    void minor_blocked_by_action_and_quiet_hours() {
        // 非宵禁 + 未超时长：受限操作被拒，且原因含监护人路径（REQ-06 禁止静默拒绝）
        String reason = MinorGuard.denyReason(true, MinorGuard.RestrictedAction.TRADE, LocalTime.of(10, 0), 0, 40);
        assertNotNull(reason);
        assertTrue(reason.contains("未成年人"));
        assertTrue(reason.contains("监护人"));

        // 宵禁时段优先于操作判定
        String quiet = MinorGuard.denyReason(true, MinorGuard.RestrictedAction.TRADE, LocalTime.of(23, 0), 0, 40);
        assertNotNull(quiet);
        assertTrue(quiet.contains("保护时段"));

        // 时长超限优先于操作判定
        String over = MinorGuard.denyReason(true, MinorGuard.RestrictedAction.TRADE, LocalTime.of(10, 0), 40, 40);
        assertNotNull(over);
        assertTrue(over.contains("上限"));

        // 未成年但操作不限（null）且非宵禁未超时 → 放行
        assertTrue(MinorGuard.allowed(true, null, LocalTime.of(10, 0), 0, 40));
    }
}
