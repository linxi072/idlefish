package com.idlefish.trade.datascope;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.mapper.AdminUserMapper;
import org.springframework.stereotype.Component;

/**
 * 管理员数据归属迁移（ITER-T05 / REQ-10）：将某机构下的后台管理员批量重指派到目标机构。
 * <p>示例场景：机构合并/撤销时，将该机构管理员整体迁移到上级或接管机构，保证其数据与登录连续性。</p>
 */
@Component
public class AdminUserOwnershipMigrator implements OwnershipMigrator {

    private final AdminUserMapper adminUserMapper;

    public AdminUserOwnershipMigrator(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public String domain() {
        return "admin_user";
    }

    @Override
    public int migrate(long fromOrgId, long toOrgId) {
        LambdaUpdateWrapper<AdminUser> uw = new LambdaUpdateWrapper<>();
        uw.eq(AdminUser::getOrgId, fromOrgId).set(AdminUser::getOrgId, toOrgId);
        return adminUserMapper.update(null, uw);
    }
}
