package com.idlefish.trade.datascope;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据范围查询增强器单测（ITER-T05 / REQ-02/04）：内存过滤 + 编程式 wrapper 注入兜底。
 */
class DataScopeQueryEnhancerTest {

    private record Row(long id, Long orgId) {
    }

    @Test
    void inMemoryFilter_keepsOnlyVisibleOrgs() {
        List<Row> data = List.of(new Row(1, 10L), new Row(2, 20L), new Row(3, 30L));
        List<Row> filtered = DataScopeQueryEnhancer.filterInMemory(data, Row::orgId, Set.of(10L, 30L));
        assertEquals(2, filtered.size());
        assertTrue(filtered.stream().map(Row::id).toList().containsAll(List.of(1L, 3L)));
    }

    @Test
    void inMemoryFilter_nullOrgIds_passesAll() {
        List<Row> data = List.of(new Row(1, 10L), new Row(2, 20L));
        List<Row> filtered = DataScopeQueryEnhancer.filterInMemory(data, Row::orgId, null);
        assertEquals(2, filtered.size());
    }

    @Test
    void inMemoryFilter_excludesNullOrgIdRows() {
        List<Row> data = List.of(new Row(1, 10L), new Row(2, null));
        List<Row> filtered = DataScopeQueryEnhancer.filterInMemory(data, Row::orgId, Set.of(10L));
        assertEquals(1, filtered.size());
        assertEquals(1L, filtered.get(0).id());
    }
}
