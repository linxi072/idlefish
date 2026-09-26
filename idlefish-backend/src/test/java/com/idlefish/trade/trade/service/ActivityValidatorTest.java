package com.idlefish.trade.trade.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.trade.entity.Activity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 活动业务校验纯函数单测（F-13.3，离线可跑，无 DB 依赖）。
 */
class ActivityValidatorTest {

    private Activity ongoing() {
        LocalDateTime now = LocalDateTime.now();
        Activity a = new Activity();
        a.setStatus("ONGOING");
        a.setStock(10);
        a.setStartAt(now.minusHours(1));
        a.setEndAt(now.plusHours(1));
        return a;
    }

    @Test
    @DisplayName("assertOngoing：进行中且处于时间窗内不抛异常")
    void ongoingOk() {
        Activity a = ongoing();
        ActivityValidator.assertOngoing(a, LocalDateTime.now());
    }

    @Test
    @DisplayName("assertOngoing：活动不存在抛 ACTIVITY_NOT_FOUND")
    void notFound() {
        BizException ex = assertThrows(BizException.class,
                () -> ActivityValidator.assertOngoing(null, LocalDateTime.now()));
        assertEquals(com.idlefish.trade.common.Code.ACTIVITY_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("assertOngoing：状态非 ONGOING 抛 ACTIVITY_NOT_ONGOING")
    void notOngoing() {
        Activity a = ongoing();
        a.setStatus("ENDED");
        assertThrows(BizException.class, () -> ActivityValidator.assertOngoing(a, LocalDateTime.now()));
    }

    @Test
    @DisplayName("assertOngoing：未到开始时间抛异常")
    void beforeStart() {
        Activity a = ongoing();
        assertThrows(BizException.class,
                () -> ActivityValidator.assertOngoing(a, a.getStartAt().minusMinutes(1)));
    }

    @Test
    @DisplayName("assertOngoing：已过结束时间抛异常")
    void afterEnd() {
        Activity a = ongoing();
        assertThrows(BizException.class,
                () -> ActivityValidator.assertOngoing(a, a.getEndAt().plusMinutes(1)));
    }

    @Test
    @DisplayName("assertStock：库存充足不抛异常")
    void stockOk() {
        ActivityValidator.assertStock(ongoing(), 5);
    }

    @Test
    @DisplayName("assertStock：库存不足抛 STOCK_NOT_ENOUGH")
    void stockNotEnough() {
        assertThrows(BizException.class, () -> ActivityValidator.assertStock(ongoing(), 11));
    }

    @Test
    @DisplayName("assertUserLimit：未达上限不抛异常")
    void userLimitOk() {
        ActivityValidator.assertUserLimit(0, 1);
        ActivityValidator.assertUserLimit(1, 2);
    }

    @Test
    @DisplayName("assertUserLimit：达到上限抛 ACTIVITY_JOIN_LIMIT")
    void userLimitExceeded() {
        assertThrows(BizException.class, () -> ActivityValidator.assertUserLimit(1, 1));
    }

    @Test
    @DisplayName("assertUserLimit：limit<=0 视为不限，任意次数均通过")
    void userLimitUnlimited() {
        ActivityValidator.assertUserLimit(99, 0);
        ActivityValidator.assertUserLimit(100, -1);
    }
}
