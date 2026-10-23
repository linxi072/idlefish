package com.idlefish.trade.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件类型注册表单测（ITER-T06 / REQ-01/02）。
 */
class EventTypeTest {

    /** G1：事件类型注册 ≥15 类。 */
    @Test
    void registeredTypesAtLeast15() {
        assertTrue(EventType.values().length >= 15, "事件类型应注册至少 15 类，实际 " + EventType.values().length);
    }

    /** 每类事件 code 唯一（枚举名即 code，天然唯一）且 schema 版本 ≥1。 */
    @Test
    void everyTypeHasVersionAtLeast1() {
        for (EventType t : EventType.values()) {
            assertTrue(t.getVersion() >= 1, t.name() + " schema 版本应 ≥1");
            assertNotNull(t.getDesc());
            assertEquals(t.name(), EventType.fromCode(t.name()).name());
        }
    }

    /** REQ-03/04：订单与资金域事件全部持久化（可回放）。 */
    @Test
    void orderAndFundEventsPersisted() {
        EventType[] fundCritical = {
                EventType.ORDER_CREATED, EventType.ORDER_PAID, EventType.ORDER_SHIPPED,
                EventType.ORDER_COMPLETED, EventType.ORDER_CANCELLED, EventType.ORDER_REFUNDED,
                EventType.PAYMENT_SUCCEEDED, EventType.PROFIT_SHARE_EXECUTED,
                EventType.WITHDRAWAL_COMPLETED, EventType.REFUND_EXECUTED};
        for (EventType t : fundCritical) {
            assertTrue(t.isPersisted(), t.name() + " 应持久化到事件存储（REQ-09）");
        }
    }

    /** fromCode：大小写敏感，未知 code 返回 null（调用方按 EVENT_TYPE_UNSUPPORTED 处理）。 */
    @Test
    void fromCodeUnknownReturnsNull() {
        assertNull(EventType.fromCode(null));
        assertNull(EventType.fromCode(""));
        assertNull(EventType.fromCode("NOT_A_TYPE"));
        assertNull(EventType.fromCode("order_paid")); // 小写不匹配
        assertEquals(EventType.ORDER_PAID, EventType.fromCode("ORDER_PAID"));
    }
}
