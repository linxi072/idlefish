package com.idlefish.trade.compliance;

import com.idlefish.trade.notify.enums.NotificationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 通知分类纯函数单测（ITER-U05 REQ-09/REQ-10）：营销类可关闭，交易必需类不可关闭。 */
class NotificationCategoryTest {

    @Test
    void marketing_types_are_classified() {
        assertTrue(NotificationCategory.isMarketing(NotificationType.COUPON_CLAIMED));
        assertTrue(NotificationCategory.isMarketing(NotificationType.POINT_EARNED));
        assertTrue(NotificationCategory.isMarketing(NotificationType.GROWTH_EARNED));
        assertTrue(NotificationCategory.isMarketing(NotificationType.GROWTH_LEVEL_UPGRADED));
        assertTrue(NotificationCategory.isMarketing(NotificationType.GROWTH_DECAY_WARNING));
        assertEquals(NotificationCategory.MARKETING, NotificationCategory.of(NotificationType.COUPON_CLAIMED));
    }

    @Test
    void essential_types_never_suppressed() {
        // 资金/履约/维权链路无论如何都必须触达
        NotificationType[] essential = {
                NotificationType.ORDER_PAID,
                NotificationType.ORDER_SHIPPED,
                NotificationType.REFUND_SUCCESS,
                NotificationType.WITHDRAW_APPROVE,
                NotificationType.DISPUTE_PLATFORM,
                NotificationType.WORKORDER_SLA_BREACHED,
                NotificationType.SYSTEM_ALERT
        };
        for (NotificationType t : essential) {
            assertFalse(NotificationCategory.isMarketing(t), t.name() + " 不得被营销开关拦截");
            assertEquals(NotificationCategory.ESSENTIAL, NotificationCategory.of(t));
        }
    }

    @Test
    void unknown_or_null_defaults_to_essential() {
        assertFalse(NotificationCategory.isMarketing(null));
        assertEquals(NotificationCategory.ESSENTIAL, NotificationCategory.of(null));
    }
}
