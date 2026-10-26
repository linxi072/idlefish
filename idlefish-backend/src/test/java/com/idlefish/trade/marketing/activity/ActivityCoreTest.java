package com.idlefish.trade.marketing.activity;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一活动内核校验纯函数单测（ITER-F08 / REQ-01、REQ-03、REQ-12、REQ-13、REQ-14）。
 * 离线可跑，无数据库 / 缓存依赖，覆盖 ActivityCore 全部分支。
 */
class ActivityCoreTest {

    private MarketingActivity ongoingActivity() {
        MarketingActivity a = new MarketingActivity();
        a.setId(1L);
        a.setKind("LOTTERY");
        a.setStatus("ONGOING");
        a.setStartAt(LocalDateTime.now().minusDays(1));
        a.setEndAt(LocalDateTime.now().plusDays(1));
        a.setStock(100);
        a.setLimitPerUser(0);
        a.setAudienceType("ALL");
        return a;
    }

    private User user(LocalDateTime createdAt, Integer creditScore, Integer realNameVerified) {
        User u = new User();
        u.setId(9L);
        u.setCreatedAt(createdAt);
        u.setCreditScore(creditScore);
        u.setRealNameVerified(realNameVerified);
        return u;
    }

    // ===== assertOngoing =====

    @Test
    void assertOngoing_null_throws() {
        BizException ex = assertThrows(BizException.class,
                () -> ActivityCore.assertOngoing(null, LocalDateTime.now()));
        assertEquals(Code.ACTIVITY_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void assertOngoing_notOngoing_throws() {
        MarketingActivity a = ongoingActivity();
        a.setStatus("DRAFT");
        BizException ex = assertThrows(BizException.class,
                () -> ActivityCore.assertOngoing(a, LocalDateTime.now()));
        assertEquals(Code.ACTIVITY_NOT_ONGOING.getCode(), ex.getCode());
    }

    @Test
    void assertOngoing_beforeStart_throws() {
        MarketingActivity a = ongoingActivity();
        a.setStartAt(LocalDateTime.now().plusDays(1));
        BizException ex = assertThrows(BizException.class,
                () -> ActivityCore.assertOngoing(a, LocalDateTime.now()));
        assertEquals(Code.ACTIVITY_NOT_ONGOING.getCode(), ex.getCode());
    }

    @Test
    void assertOngoing_afterEnd_throws() {
        MarketingActivity a = ongoingActivity();
        a.setEndAt(LocalDateTime.now().minusDays(1));
        BizException ex = assertThrows(BizException.class,
                () -> ActivityCore.assertOngoing(a, LocalDateTime.now()));
        assertEquals(Code.ACTIVITY_NOT_ONGOING.getCode(), ex.getCode());
    }

    @Test
    void assertOngoing_ok() {
        // 不抛即通过
        ActivityCore.assertOngoing(ongoingActivity(), LocalDateTime.now());
    }

    // ===== assertStock / canDeduct =====

    @Test
    void assertStock_insufficient_throws() {
        MarketingActivity a = ongoingActivity();
        a.setStock(5);
        BizException ex = assertThrows(BizException.class, () -> ActivityCore.assertStock(a, 6));
        assertEquals(Code.STOCK_NOT_ENOUGH.getCode(), ex.getCode());
    }

    @Test
    void assertStock_enough_ok() {
        MarketingActivity a = ongoingActivity();
        a.setStock(10);
        ActivityCore.assertStock(a, 6);
    }

    @Test
    void canDeduct_null_returnsFalse() {
        assertFalse(ActivityCore.canDeduct(null, 1));
    }

    @Test
    void canDeduct_enough_returnsTrue() {
        assertTrue(ActivityCore.canDeduct(5, 3));
    }

    @Test
    void canDeduct_insufficient_returnsFalse() {
        assertFalse(ActivityCore.canDeduct(2, 5));
    }

    // ===== assertUserLimit =====

    @Test
    void assertUserLimit_zeroLimit_ok() {
        ActivityCore.assertUserLimit(100, 0);
    }

    @Test
    void assertUserLimit_reached_throws() {
        BizException ex = assertThrows(BizException.class, () -> ActivityCore.assertUserLimit(3, 3));
        assertEquals(Code.ACTIVITY_JOIN_LIMIT.getCode(), ex.getCode());
    }

    @Test
    void assertUserLimit_notReached_ok() {
        ActivityCore.assertUserLimit(2, 3);
    }

    // ===== assertAudience（REQ-13）=====

    @Test
    void assertAudience_all_ok() {
        ActivityCore.assertAudience(ongoingActivity(), null, LocalDateTime.now());
    }

    @Test
    void assertAudience_newUser_nullUser_throws() {
        MarketingActivity a = ongoingActivity();
        a.setAudienceType("NEW_USER");
        BizException ex = assertThrows(BizException.class,
                () -> ActivityCore.assertAudience(a, null, LocalDateTime.now()));
        assertEquals(Code.AUDIENCE_NOT_MATCH.getCode(), ex.getCode());
    }

    @Test
    void assertAudience_newUser_nullCreatedAt_throws() {
        MarketingActivity a = ongoingActivity();
        a.setAudienceType("NEW_USER");
        BizException ex = assertThrows(BizException.class,
                () -> ActivityCore.assertAudience(a, user(null, null, null), LocalDateTime.now()));
        assertEquals(Code.AUDIENCE_NOT_MATCH.getCode(), ex.getCode());
    }

    @Test
    void assertAudience_newUser_expired_throws() {
        MarketingActivity a = ongoingActivity();
        a.setAudienceType("NEW_USER");
        a.setAudienceValue("30");
        // 注册于 60 天前，超过 30 天窗口
        BizException ex = assertThrows(BizException.class, () -> ActivityCore.assertAudience(
                a, user(LocalDateTime.now().minusDays(60), null, null), LocalDateTime.now()));
        assertEquals(Code.AUDIENCE_NOT_MATCH.getCode(), ex.getCode());
    }

    @Test
    void assertAudience_newUser_withinWindow_ok() {
        MarketingActivity a = ongoingActivity();
        a.setAudienceType("NEW_USER");
        a.setAudienceValue("30");
        ActivityCore.assertAudience(a, user(LocalDateTime.now().minusDays(10), null, null), LocalDateTime.now());
    }

    @Test
    void assertAudience_level_nullUser_throws() {
        MarketingActivity a = ongoingActivity();
        a.setAudienceType("LEVEL");
        BizException ex = assertThrows(BizException.class,
                () -> ActivityCore.assertAudience(a, null, LocalDateTime.now()));
        assertEquals(Code.AUDIENCE_NOT_MATCH.getCode(), ex.getCode());
    }

    @Test
    void assertAudience_level_nullScore_throws() {
        MarketingActivity a = ongoingActivity();
        a.setAudienceType("LEVEL");
        BizException ex = assertThrows(BizException.class,
                () -> ActivityCore.assertAudience(a, user(LocalDateTime.now(), null, null), LocalDateTime.now()));
        assertEquals(Code.AUDIENCE_NOT_MATCH.getCode(), ex.getCode());
    }

    @Test
    void assertAudience_level_belowMin_throws() {
        MarketingActivity a = ongoingActivity();
        a.setAudienceType("LEVEL");
        a.setAudienceValue("500");
        BizException ex = assertThrows(BizException.class, () -> ActivityCore.assertAudience(
                a, user(LocalDateTime.now(), 400, null), LocalDateTime.now()));
        assertEquals(Code.AUDIENCE_NOT_MATCH.getCode(), ex.getCode());
    }

    @Test
    void assertAudience_level_ok() {
        MarketingActivity a = ongoingActivity();
        a.setAudienceType("LEVEL");
        a.setAudienceValue("500");
        ActivityCore.assertAudience(a, user(LocalDateTime.now(), 600, null), LocalDateTime.now());
    }

    @Test
    void assertAudience_realName_nullUser_throws() {
        MarketingActivity a = ongoingActivity();
        a.setAudienceType("REALNAME");
        BizException ex = assertThrows(BizException.class,
                () -> ActivityCore.assertAudience(a, null, LocalDateTime.now()));
        assertEquals(Code.AUDIENCE_NOT_MATCH.getCode(), ex.getCode());
    }

    @Test
    void assertAudience_realName_notVerified_throws() {
        MarketingActivity a = ongoingActivity();
        a.setAudienceType("REALNAME");
        BizException ex = assertThrows(BizException.class, () -> ActivityCore.assertAudience(
                a, user(LocalDateTime.now(), null, 0), LocalDateTime.now()));
        assertEquals(Code.AUDIENCE_NOT_MATCH.getCode(), ex.getCode());
    }

    @Test
    void assertAudience_realName_verified_ok() {
        MarketingActivity a = ongoingActivity();
        a.setAudienceType("REALNAME");
        ActivityCore.assertAudience(a, user(LocalDateTime.now(), null, 1), LocalDateTime.now());
    }

    // ===== assertLossSafe（REQ-14）=====

    @Test
    void assertLossSafe_noCost_ok() {
        ActivityCore.assertLossSafe(0, 0);
        ActivityCore.assertLossSafe(10, 0);
    }

    @Test
    void assertLossSafe_floorAboveCost_ok() {
        ActivityCore.assertLossSafe(1000, 800);
    }

    @Test
    void assertLossSafe_floorBelowCost_throws() {
        BizException ex = assertThrows(BizException.class, () -> ActivityCore.assertLossSafe(500, 800));
        assertEquals(Code.ACTIVITY_LOSS_RISK.getCode(), ex.getCode());
    }

    // ===== isOpen =====

    @Test
    void isOpen_null_false() {
        assertFalse(ActivityCore.isOpen(null, LocalDateTime.now()));
    }

    @Test
    void isOpen_notOngoing_false() {
        MarketingActivity a = ongoingActivity();
        a.setStatus("DRAFT");
        assertFalse(ActivityCore.isOpen(a, LocalDateTime.now()));
    }

    @Test
    void isOpen_beforeStart_false() {
        MarketingActivity a = ongoingActivity();
        a.setStartAt(LocalDateTime.now().plusDays(1));
        assertFalse(ActivityCore.isOpen(a, LocalDateTime.now()));
    }

    @Test
    void isOpen_afterEnd_false() {
        MarketingActivity a = ongoingActivity();
        a.setEndAt(LocalDateTime.now().minusDays(1));
        assertFalse(ActivityCore.isOpen(a, LocalDateTime.now()));
    }

    @Test
    void isOpen_ok() {
        assertTrue(ActivityCore.isOpen(ongoingActivity(), LocalDateTime.now()));
    }
}
