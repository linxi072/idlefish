package com.idlefish.trade.datascope;

import java.util.Set;

/**
 * 数据范围上下文（ThreadLocal）：承载当前请求可见的机构集合。
 * <p>{@code ALL} 范围时 {@link #orgIds} 为 {@code null}，表示不受限（unrestricted）。
 * 统一过滤层（{@code DataScopeDataPermissionHandler}）与业务网关（{@code DataScopeService.assertAccessible}）
 * 均从此处读取当前范围。</p>
 */
public final class DataScopeContext {

    private static final ThreadLocal<DataScopeContext> HOLDER = new ThreadLocal<>();

    private final long adminId;
    private final long selfOrgId;
    private final DataScope scope;
    private final Set<Long> orgIds; // null = 不受限（ALL）

    private DataScopeContext(long adminId, long selfOrgId, DataScope scope, Set<Long> orgIds) {
        this.adminId = adminId;
        this.selfOrgId = selfOrgId;
        this.scope = scope;
        this.orgIds = orgIds;
    }

    public static void set(DataScopeContext ctx) {
        HOLDER.set(ctx);
    }

    public static DataScopeContext get() {
        return HOLDER.get();
    }

    /** 清除当前线程上下文（请求结束时必须调用，避免线程复用串扰）。 */
    public static void clear() {
        HOLDER.remove();
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

    /** 构造工厂。 */
    public static DataScopeContext of(long adminId, long selfOrgId, DataScope scope, Set<Long> orgIds) {
        return new DataScopeContext(adminId, selfOrgId, scope, orgIds);
    }
}
