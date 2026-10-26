package com.idlefish.trade.meta;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.meta.vo.StatusGroupVO;
import com.idlefish.trade.meta.vo.StatusMetaVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StatusMetaResolver 纯函数单测（离线、无 Spring、无 MySQL）。
 */
class StatusMetaResolverTest {

    @Test
    void allGroups_nonEmpty_and_covers_required_domains() {
        List<StatusGroupVO> groups = StatusMetaResolver.allGroups();
        assertEquals(6, groups.size());
        List<String> domains = groups.stream().map(StatusGroupVO::domain).toList();
        assertTrue(domains.containsAll(List.of("order", "item", "pay", "refund", "dispute", "audit")));
    }

    @Test
    void resolveDomain_ok() {
        StatusGroupVO g = StatusMetaResolver.resolveDomain("order");
        assertEquals("订单状态", g.label());
        assertEquals(6, g.items().size());
        // 终态标记正确
        assertTrue(g.items().stream().anyMatch(m -> "completed".equals(m.code()) && m.terminal()));
        assertTrue(g.items().stream().anyMatch(m -> "pending_pay".equals(m.code()) && m.open()));
    }

    @Test
    void resolveDomain_unknown_throws_param_invalid() {
        BizException ex = assertThrows(BizException.class,
                () -> StatusMetaResolver.resolveDomain("nope"));
        assertEquals(Code.PARAM_INVALID.getCode(), ex.getCode());
    }

    @Test
    void resolveDomain_blank_throws() {
        assertThrows(BizException.class, () -> StatusMetaResolver.resolveDomain("  "));
    }

    @Test
    void resolveOne_ok() {
        StatusMetaVO m = StatusMetaResolver.resolveOne("item", "onsale");
        assertEquals("在售", m.name());
        assertEquals("#67C23A", m.color());
        assertFalse(m.terminal());
        assertFalse(m.open());
    }

    @Test
    void resolveOne_unknown_code_throws() {
        BizException ex = assertThrows(BizException.class,
                () -> StatusMetaResolver.resolveOne("item", "ghost"));
        assertEquals(Code.PARAM_INVALID.getCode(), ex.getCode());
    }

    @Test
    void dispute_states_present_and_semantics() {
        StatusGroupVO g = StatusMetaResolver.resolveDomain("dispute");
        assertEquals(6, g.items().size());
        assertTrue(g.items().stream().anyMatch(m -> "PLATFORM".equals(m.code()) && m.open() && !m.terminal()));
        assertTrue(g.items().stream().anyMatch(m -> "CLOSED".equals(m.code()) && m.terminal()));
    }
}
