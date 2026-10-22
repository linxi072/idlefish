package com.idlefish.trade.datascope.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.mapper.AdminUserMapper;
import com.idlefish.trade.datascope.DataScope;
import com.idlefish.trade.datascope.DataScopeContext;
import com.idlefish.trade.datascope.DataScopeDecision;
import com.idlefish.trade.datascope.DataScopeException;
import com.idlefish.trade.datascope.DataScopeResolver;
import com.idlefish.trade.datascope.OrgHierarchy;
import com.idlefish.trade.datascope.OrgRef;
import com.idlefish.trade.datascope.OwnershipMigrator;
import com.idlefish.trade.risk.entity.AuditLog;
import com.idlefish.trade.risk.mapper.AuditLogMapper;
import com.idlefish.trade.system.entity.AdminUserRole;
import com.idlefish.trade.system.entity.SysOrganization;
import com.idlefish.trade.system.entity.SysRole;
import com.idlefish.trade.system.mapper.AdminUserRoleMapper;
import com.idlefish.trade.system.mapper.SysOrganizationMapper;
import com.idlefish.trade.system.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据范围服务（ITER-T05 核心，REQ-02/03/05/06/09/10）。
 * <ul>
 *   <li>{@link #resolveForAdmin}：依据管理员角色 dataScope 合并出可见机构集合，并叠加临时授权（REQ-02/05/09）。</li>
 *   <li>{@link #assertAccessible}：越权访问时写审计日志并拒绝（REQ-03）。</li>
 *   <li>{@link #isLoginAllowed}：机构停用则禁止登录（REQ-06）。</li>
 *   <li>{@link #migrateData}：重指派数据归属（REQ-10）。</li>
 * </ul>
 * 默认从严（REQ-09）：角色未声明范围按 SELF 处理。
 */
@Service
public class DataScopeService {

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysOrganizationMapper sysOrganizationMapper;
    private final AuditLogMapper auditLogMapper;
    private final TempGrantService tempGrantService;
    private final List<OwnershipMigrator> migrators;

    public DataScopeService(AdminUserMapper adminUserMapper,
                            AdminUserRoleMapper adminUserRoleMapper,
                            SysRoleMapper sysRoleMapper,
                            SysOrganizationMapper sysOrganizationMapper,
                            AuditLogMapper auditLogMapper,
                            TempGrantService tempGrantService,
                            List<OwnershipMigrator> migrators) {
        this.adminUserMapper = adminUserMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysOrganizationMapper = sysOrganizationMapper;
        this.auditLogMapper = auditLogMapper;
        this.tempGrantService = tempGrantService;
        this.migrators = migrators == null ? new ArrayList<>() : migrators;
    }

    /**
     * 解析某管理员的可见机构决议：合并其全部角色的 dataScope，并叠加生效中的临时授权。
     */
    public DataScopeDecision resolveForAdmin(long adminId) {
        LocalDateTime now = LocalDateTime.now();
        AdminUser u = adminUserMapper.selectById(adminId);
        long selfOrgId = (u != null && u.getOrgId() != null) ? u.getOrgId() : 0L;

        OrgHierarchy hierarchy = loadHierarchy();
        DataScopeResolver resolver = new DataScopeResolver(hierarchy);

        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getAdminUserId, adminId));
        if (userRoles.isEmpty()) {
            // 未分配任何角色：默认最严 SELF（REQ-09）
            return new DataScopeDecision(adminId, selfOrgId, DataScope.SELF,
                    new LinkedHashSet<>(Set.of(selfOrgId)));
        }

        Set<Long> merged = new LinkedHashSet<>();
        DataScope widest = DataScope.SELF;
        boolean anyAll = false;
        for (AdminUserRole ur : userRoles) {
            SysRole role = sysRoleMapper.selectById(ur.getRoleId());
            if (role == null) {
                continue;
            }
            DataScope scope = DataScope.fromCode(role.getDataScope());
            Set<Long> roleOrgIds = resolver.resolve(selfOrgId, scope,
                    TempGrantService.parseOrgIds(role.getDataOrgIds()));
            if (roleOrgIds == null) {
                anyAll = true;
                break;
            }
            merged.addAll(roleOrgIds);
            if (scope.rank() > widest.rank()) {
                widest = scope;
            }
        }

        if (anyAll) {
            return DataScopeDecision.unrestricted(adminId, selfOrgId);
        }

        Set<Long> finalOrgIds = tempGrantService.effectiveOrgIds(merged,
                tempGrantService.listActive(adminId, now), now);
        return new DataScopeDecision(adminId, selfOrgId, widest, finalOrgIds);
    }

    /**
     * 越权判定（强制）：不可见则写审计日志并抛出 {@link DataScopeException}（REQ-03）。
     */
    public void assertAccessible(long adminId, long targetOrgId) {
        DataScopeDecision d = resolveForAdmin(adminId);
        if (d.isUnrestricted() || (d.getOrgIds() != null && d.getOrgIds().contains(targetOrgId))) {
            return;
        }
        writeDenyAudit(adminId, targetOrgId);
        throw new DataScopeException("管理员 " + adminId + " 无权访问机构 " + targetOrgId
                + "（范围：" + d.getScope() + "）");
    }

    /**
     * 越权预览（非强制）：仅返回是否可见，不抛异常、不写审计。
     */
    public boolean isAccessible(long adminId, long targetOrgId) {
        DataScopeDecision d = resolveForAdmin(adminId);
        return d.isUnrestricted() || (d.getOrgIds() != null && d.getOrgIds().contains(targetOrgId));
    }

    /**
     * 返回管理员可见机构集合（ALL 时返回全量机构 ID）。
     */
    public Set<Long> orgsInScope(long adminId) {
        DataScopeDecision d = resolveForAdmin(adminId);
        if (d.isUnrestricted()) {
            return loadHierarchy().allIds();
        }
        return d.getOrgIds() == null ? new HashSet<>() : d.getOrgIds();
    }

    /**
     * 是否允许登录（REQ-06）：账号禁用或所属机构停用则禁止登录。
     */
    public boolean isLoginAllowed(long adminId) {
        AdminUser u = adminUserMapper.selectById(adminId);
        if (u == null || (u.getStatus() != null && u.getStatus() == 0)) {
            return false;
        }
        if (u.getOrgId() == null) {
            return true;
        }
        SysOrganization org = sysOrganizationMapper.selectById(u.getOrgId());
        return org == null || org.getStatus() == null || org.getStatus() != 0;
    }

    /**
     * 数据归属迁移（REQ-10）：遍历全部 {@link OwnershipMigrator} 实现，返回受影响总行数。
     */
    public int migrateData(long adminId, long fromOrgId, long toOrgId) {
        int total = 0;
        for (OwnershipMigrator m : migrators) {
            total += m.migrate(fromOrgId, toOrgId);
        }
        return total;
    }

    private OrgHierarchy loadHierarchy() {
        List<SysOrganization> orgs = sysOrganizationMapper.selectList(null);
        List<OrgRef> refs = new ArrayList<>();
        if (orgs != null) {
            for (SysOrganization o : orgs) {
                long pid = o.getParentId() == null ? OrgRef.ROOT_PARENT : o.getParentId();
                refs.add(new OrgRef(o.getId() == null ? 0L : o.getId(), pid));
            }
        }
        return new OrgHierarchy(refs);
    }

    private void writeDenyAudit(long adminId, long targetOrgId) {
        try {
            AuditLog log = new AuditLog();
            log.setOperatorId(adminId);
            log.setAction("DATA_SCOPE_DENY");
            log.setTargetType("org");
            log.setTargetId(String.valueOf(targetOrgId));
            log.setDetail("adminId=" + adminId + ", targetOrgId=" + targetOrgId);
            auditLogMapper.insert(log);
        } catch (Exception ignore) {
            // 审计写失败不应阻断主流程
        }
    }
}
