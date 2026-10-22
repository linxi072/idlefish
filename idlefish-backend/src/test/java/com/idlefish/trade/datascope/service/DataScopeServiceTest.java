package com.idlefish.trade.datascope.service;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.mapper.AdminUserMapper;
import com.idlefish.trade.datascope.DataScope;
import com.idlefish.trade.datascope.DataScopeContext;
import com.idlefish.trade.datascope.DataScopeDecision;
import com.idlefish.trade.datascope.DataScopeException;
import com.idlefish.trade.datascope.OwnershipMigrator;
import com.idlefish.trade.risk.entity.AuditLog;
import com.idlefish.trade.risk.mapper.AuditLogMapper;
import com.idlefish.trade.system.entity.AdminUserRole;
import com.idlefish.trade.system.entity.SysOrganization;
import com.idlefish.trade.system.entity.SysRole;
import com.idlefish.trade.system.mapper.AdminUserRoleMapper;
import com.idlefish.trade.system.mapper.SysOrganizationMapper;
import com.idlefish.trade.system.mapper.SysRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据范围服务单测（ITER-T05 / REQ-02/03/05/06/09/10）：范围解析、合并、越权审计、登录门禁、迁移。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataScopeServiceTest {

    @Mock private AdminUserMapper adminUserMapper;
    @Mock private AdminUserRoleMapper adminUserRoleMapper;
    @Mock private SysRoleMapper sysRoleMapper;
    @Mock private SysOrganizationMapper sysOrganizationMapper;
    @Mock private AuditLogMapper auditLogMapper;
    @Mock private TempGrantService tempGrantService;
    @Mock private OwnershipMigrator migrator;

    private DataScopeService service;

    @BeforeEach
    void setUp() {
        service = new DataScopeService(adminUserMapper, adminUserRoleMapper, sysRoleMapper,
                sysOrganizationMapper, auditLogMapper, tempGrantService, new ArrayList<>(List.of(migrator)));
        // 默认：临时授权不影响（保持基础范围）
        lenient().when(tempGrantService.listActive(anyLong(), any())).thenReturn(List.of());
        lenient().when(tempGrantService.effectiveOrgIds(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private AdminUser admin(long id, Long orgId, int status) {
        AdminUser u = new AdminUser();
        u.setId(id);
        u.setOrgId(orgId);
        u.setStatus(status);
        return u;
    }

    private SysRole role(String dataScope, String dataOrgIds) {
        SysRole r = new SysRole();
        r.setId(1L);
        r.setDataScope(dataScope);
        r.setDataOrgIds(dataOrgIds);
        return r;
    }

    private com.idlefish.trade.system.entity.AdminUserRole aur(long id, long adminId, long roleId) {
        com.idlefish.trade.system.entity.AdminUserRole r = new com.idlefish.trade.system.entity.AdminUserRole();
        r.setId(id);
        r.setAdminUserId(adminId);
        r.setRoleId(roleId);
        return r;
    }

    private List<SysOrganization> orgTree() {
        SysOrganization o1 = new SysOrganization(); o1.setId(1L); o1.setParentId(0L);
        SysOrganization o2 = new SysOrganization(); o2.setId(2L); o2.setParentId(1L);
        SysOrganization o3 = new SysOrganization(); o3.setId(3L); o3.setParentId(1L);
        SysOrganization o4 = new SysOrganization(); o4.setId(4L); o4.setParentId(2L);
        return List.of(o1, o2, o3, o4);
    }

    @Test
    void noRoles_defaultsToSelf() {
        when(adminUserMapper.selectById(1L)).thenReturn(admin(1L, 1L, 1));
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of());
        DataScopeDecision d = service.resolveForAdmin(1L);
        assertEquals(DataScope.SELF, d.getScope());
        assertEquals(Set.of(1L), d.getOrgIds());
    }

    @Test
    void selfScope_onlyOwnOrg() {
        when(adminUserMapper.selectById(1L)).thenReturn(admin(1L, 1L, 1));
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(aur(1L, 1L, 1L)));
        when(sysRoleMapper.selectById(1L)).thenReturn(role("SELF", ""));
        when(sysOrganizationMapper.selectList(any())).thenReturn(orgTree());
        DataScopeDecision d = service.resolveForAdmin(1L);
        assertEquals(DataScope.SELF, d.getScope());
        assertEquals(Set.of(1L), d.getOrgIds());
    }

    @Test
    void selfAndChild_includesDescendants() {
        when(adminUserMapper.selectById(1L)).thenReturn(admin(1L, 1L, 1));
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(aur(1L, 1L, 1L)));
        when(sysRoleMapper.selectById(1L)).thenReturn(role("SELF_AND_CHILD", ""));
        when(sysOrganizationMapper.selectList(any())).thenReturn(orgTree());
        DataScopeDecision d = service.resolveForAdmin(1L);
        assertEquals(DataScope.SELF_AND_CHILD, d.getScope());
        assertEquals(Set.of(1L, 2L, 3L, 4L), d.getOrgIds());
    }

    @Test
    void allScope_unrestricted() {
        when(adminUserMapper.selectById(1L)).thenReturn(admin(1L, 1L, 1));
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(aur(1L, 1L, 1L)));
        when(sysRoleMapper.selectById(1L)).thenReturn(role("ALL", ""));
        when(sysOrganizationMapper.selectList(any())).thenReturn(orgTree());
        DataScopeDecision d = service.resolveForAdmin(1L);
        assertTrue(d.isUnrestricted());
    }

    @Test
    void appoint_usesSpecifiedOrgs() {
        when(adminUserMapper.selectById(1L)).thenReturn(admin(1L, 1L, 1));
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(aur(1L, 1L, 1L)));
        when(sysRoleMapper.selectById(1L)).thenReturn(role("APPOINT", "5,6"));
        when(sysOrganizationMapper.selectList(any())).thenReturn(orgTree());
        DataScopeDecision d = service.resolveForAdmin(1L);
        assertEquals(DataScope.APPOINT, d.getScope());
        assertEquals(Set.of(5L, 6L), d.getOrgIds());
    }

    @Test
    void tempGrant_mergedIntoScope() {
        com.idlefish.trade.datascope.entity.DataScopeTempGrant g = new com.idlefish.trade.datascope.entity.DataScopeTempGrant();
        g.setStatus(1);
        g.setExpiredAt(java.time.LocalDateTime.now().plusDays(1));
        g.setOrgIds("99");
        when(tempGrantService.listActive(anyLong(), any())).thenReturn(List.of(g));
        when(tempGrantService.effectiveOrgIds(any(), any(), any())).thenAnswer(inv -> {
            Set<Long> base = inv.getArgument(0);
            Set<Long> m = new LinkedHashSet<>(base);
            m.add(99L);
            return m;
        });
        when(adminUserMapper.selectById(1L)).thenReturn(admin(1L, 1L, 1));
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(aur(1L, 1L, 1L)));
        when(sysRoleMapper.selectById(1L)).thenReturn(role("SELF", ""));
        when(sysOrganizationMapper.selectList(any())).thenReturn(orgTree());
        DataScopeDecision d = service.resolveForAdmin(1L);
        assertTrue(d.getOrgIds().contains(99L));
    }

    @Test
    void assertAccessible_allowedWithinScope() {
        when(adminUserMapper.selectById(1L)).thenReturn(admin(1L, 1L, 1));
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(aur(1L, 1L, 1L)));
        when(sysRoleMapper.selectById(1L)).thenReturn(role("SELF", ""));
        when(sysOrganizationMapper.selectList(any())).thenReturn(orgTree());
        service.assertAccessible(1L, 1L); // 自身机构，不抛
    }

    @Test
    void assertAccessible_deniedWritesAuditAndThrows() {
        when(adminUserMapper.selectById(1L)).thenReturn(admin(1L, 1L, 1));
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(aur(1L, 1L, 1L)));
        when(sysRoleMapper.selectById(1L)).thenReturn(role("SELF", ""));
        when(sysOrganizationMapper.selectList(any())).thenReturn(orgTree());
        DataScopeException ex = assertThrows(DataScopeException.class, () -> service.assertAccessible(1L, 2L));
        assertTrue(ex.getMessage().contains("2"));
        verify(auditLogMapper).insert(any(AuditLog.class));
    }

    @Test
    void isLoginAllowed_various() {
        when(sysOrganizationMapper.selectById(1L)).thenReturn(null);
        // 账号禁用
        when(adminUserMapper.selectById(10L)).thenReturn(admin(10L, 1L, 0));
        assertFalse(service.isLoginAllowed(10L));
        // 机构停用
        when(adminUserMapper.selectById(11L)).thenReturn(admin(11L, 1L, 1));
        SysOrganization disabled = new SysOrganization(); disabled.setId(1L); disabled.setStatus(0);
        when(sysOrganizationMapper.selectById(1L)).thenReturn(disabled);
        assertFalse(service.isLoginAllowed(11L));
        // 正常
        when(adminUserMapper.selectById(12L)).thenReturn(admin(12L, 2L, 1));
        SysOrganization enabled = new SysOrganization(); enabled.setId(2L); enabled.setStatus(1);
        when(sysOrganizationMapper.selectById(2L)).thenReturn(enabled);
        assertTrue(service.isLoginAllowed(12L));
    }

    @Test
    void orgsInScope_unrestrictedReturnsAll() {
        when(adminUserMapper.selectById(1L)).thenReturn(admin(1L, 1L, 1));
        when(adminUserRoleMapper.selectList(any())).thenReturn(List.of(aur(1L, 1L, 1L)));
        when(sysRoleMapper.selectById(1L)).thenReturn(role("ALL", ""));
        when(sysOrganizationMapper.selectList(any())).thenReturn(orgTree());
        assertEquals(Set.of(1L, 2L, 3L, 4L), service.orgsInScope(1L));
    }

    @Test
    void migrateData_invokesMigrators() {
        when(migrator.domain()).thenReturn("admin_user");
        when(migrator.migrate(1L, 2L)).thenReturn(5);
        int affected = service.migrateData(1L, 1L, 2L);
        assertEquals(5, affected);
        verify(migrator).migrate(1L, 2L);
    }
}
