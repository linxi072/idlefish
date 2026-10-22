package com.idlefish.trade.datascope;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据权限决议（值对象）：范围 + 可见机构集合（不可变）。
 * 由 {@code DataScopeService.resolveForAdmin} 产出，供上下文装配与网关判定消费。
 */
public class DataScopeDecision {

    private final long adminId;
    private final long selfOrgId;
    private final DataScope scope;
    private final Set<Long> orgIds; // null = ALL 不受限

    public DataScopeDecision(long adminId, long selfOrgId, DataScope scope, Set<Long> orgIds) {
        this.adminId = adminId;
        this.selfOrgId = selfOrgId;
        this.scope = scope;
        this.orgIds = orgIds == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(orgIds));
    }

    public long getAdminId() {
        return adminId;
    }

    public long getSelfOrgId() {
        return selfOrgId;
    }

    public DataScope getScope() {
        return scope;
    }

    /** 可见机构集合；{@code null} 表示不受限。 */
    public Set<Long> getOrgIds() {
        return orgIds;
    }

    public boolean isUnrestricted() {
        return orgIds == null;
    }

    /** 构造「全部」决议。 */
    public static DataScopeDecision unrestricted(long adminId, long selfOrgId) {
        return new DataScopeDecision(adminId, selfOrgId, DataScope.ALL, null);
    }
}
