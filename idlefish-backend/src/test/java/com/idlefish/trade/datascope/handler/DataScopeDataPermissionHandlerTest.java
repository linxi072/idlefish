package com.idlefish.trade.datascope.handler;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.datascope.DataScope;
import com.idlefish.trade.datascope.DataScopeContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据范围 SQL 过滤处理器单测（ITER-T05 / REQ-02/09）：按表注入 org_id IN (...)，无上下文/ALL/未配置表放行。
 */
class DataScopeDataPermissionHandlerTest {

    private final IdlefishProperties props = new IdlefishProperties();
    private final DataScopeDataPermissionHandler handler = new DataScopeDataPermissionHandler(props);

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void configuredTable_withOrgIds_injectsInClause() {
        DataScopeContext.set(DataScopeContext.of(1L, 1L, DataScope.SELF, Set.of(1L, 2L)));
        Expression expr = handler.getSqlSegment(new Table("t_admin_user"), null, "stmt");
        assertNotNull(expr);
        String sql = expr.toString().toLowerCase();
        assertTrue(sql.contains("org_id in"), "应包含 org_id IN：" + sql);
        assertTrue(sql.contains("1") && sql.contains("2"), "应包含可见机构：" + sql);
    }

    @Test
    void unrestricted_returnsNull() {
        DataScopeContext.set(DataScopeContext.of(1L, 1L, DataScope.ALL, null));
        assertNull(handler.getSqlSegment(new Table("t_admin_user"), null, "stmt"));
    }

    @Test
    void noContext_returnsNull() {
        assertNull(handler.getSqlSegment(new Table("t_admin_user"), null, "stmt"));
    }

    @Test
    void tableNotConfigured_returnsNull() {
        // 默认仅 t_admin_user 启用隔离，t_item 不在配置内
        DataScopeContext.set(DataScopeContext.of(1L, 1L, DataScope.SELF, Set.of(1L)));
        assertNull(handler.getSqlSegment(new Table("t_item"), null, "stmt"));
    }

    @Test
    void emptyOrgIds_deniesWithInZero() {
        DataScopeContext.set(DataScopeContext.of(1L, 1L, DataScope.SELF, Set.of()));
        Expression expr = handler.getSqlSegment(new Table("t_admin_user"), null, "stmt");
        assertNotNull(expr);
        assertTrue(expr.toString().toLowerCase().contains("org_id in (0)"), "空范围应拒绝全部：" + expr);
    }
}
