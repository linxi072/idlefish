package com.idlefish.trade.datascope;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据范围 → 可见机构集合（纯函数，REQ-02）。
 * <ul>
 *   <li>{@code SELF}          —— 仅 adminOrgId</li>
 *   <li>{@code SELF_AND_CHILD}—— adminOrgId + 全部下级（基于 {@link OrgHierarchy}）</li>
 *   <li>{@code APPOINT}       —— 指定的 appointedOrgIds（并集）</li>
 *   <li>{@code ALL}           —— 返回 {@code null}，表示不受限（上下文置 unrestricted）</li>
 * </ul>
 * 入参 scope 为 null 时回落默认范围（REQ-09）。
 */
public class DataScopeResolver {

    private final OrgHierarchy hierarchy;

    public DataScopeResolver(OrgHierarchy hierarchy) {
        this.hierarchy = hierarchy;
    }

    /**
     * 解析可见机构集合。
     *
     * @param adminOrgId       当前管理员所属机构
     * @param scope            数据范围（null → 默认 SELF）
     * @param appointedOrgIds  指定机构集合（APPOINT 时使用）
     * @return 可见机构集合；ALL 返回 null（不受限）
     */
    public Set<Long> resolve(long adminOrgId, DataScope scope, Set<Long> appointedOrgIds) {
        if (scope == null) {
            scope = DataScope.defaultScope();
        }
        switch (scope) {
            case ALL:
                return null;
            case SELF_AND_CHILD:
                return new LinkedHashSet<>(hierarchy.descendantsOf(adminOrgId));
            case APPOINT: {
                Set<Long> s = new LinkedHashSet<>();
                if (appointedOrgIds != null) {
                    for (Long o : appointedOrgIds) {
                        if (o != null) {
                            s.add(o);
                        }
                    }
                }
                return s;
            }
            case SELF:
            default:
                Set<Long> self = new LinkedHashSet<>();
                self.add(adminOrgId);
                return self;
        }
    }
}
