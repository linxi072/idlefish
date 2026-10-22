package com.idlefish.trade.datascope;

/**
 * 数据归属迁移器（ITER-T05 / REQ-10）：将数据从源机构重指派到目标机构。
 * <p>各业务域（管理员、商品、订单等）实现本接口并注册为 Spring Bean，
 * {@code DataScopeService.migrateData} 会遍历所有实现逐一执行，返回受影响总行数。</p>
 */
public interface OwnershipMigrator {

    /** 业务域标识（审计/日志展示用）。 */
    String domain();

    /**
     * 将归属为 {@code fromOrgId} 的数据重指派到 {@code toOrgId}。
     *
     * @return 受影响行数
     */
    int migrate(long fromOrgId, long toOrgId);
}
