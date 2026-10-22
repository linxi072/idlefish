package com.idlefish.trade.datascope.handler;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.datascope.DataScopeContext;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据范围统一过滤处理器（ITER-T05 / REQ-02/09）。
 * 基于 MyBatis-Plus {@link MultiDataPermissionHandler}：对配置了 {@code org_id} 列的表，
 * 在 SQL 的 WHERE 后追加 {@code AND org_id IN (...)}；无上下文或 ALL 范围时返回 null（不追加，放行全部）。
 * <p>范围来源于 {@link DataScopeContext}（由 {@code DataScopeInterceptor} 在请求入口装配），
 * 因此仅对「已登录管理员」的请求生效，买家/C 端请求不受任何影响。</p>
 */
@Component
public class DataScopeDataPermissionHandler implements MultiDataPermissionHandler {

    private final IdlefishProperties props;

    public DataScopeDataPermissionHandler(IdlefishProperties props) {
        this.props = props;
    }

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        DataScopeContext ctx = DataScopeContext.get();
        if (ctx == null || ctx.isUnrestricted()) {
            return null;
        }
        Set<Long> orgIds = ctx.getOrgIds();
        if (orgIds == null || orgIds.isEmpty()) {
            // 范围声明为 SELF 但无可见机构：以 org_id IN (0) 形式拒绝该表全部行（从严）
            return parse("org_id IN (0)");
        }
        List<String> scopeTables = props.getDataScope().getOrgIdTables();
        if (scopeTables == null || scopeTables.isEmpty()) {
            return null;
        }
        Set<String> configured = new HashSet<>(scopeTables);
        String tableName = table.getName() == null ? "" : table.getName().toLowerCase();
        if (!configured.contains(tableName)) {
            return null;
        }
        return parse("org_id IN (" + String.join(",", orgIds.stream().map(String::valueOf).toList()) + ")");
    }

    private Expression parse(String expr) {
        try {
            return CCJSqlParserUtil.parseExpression(expr);
        } catch (Exception e) {
            throw new IllegalStateException("数据范围 SQL 片段解析失败: " + expr, e);
        }
    }
}
