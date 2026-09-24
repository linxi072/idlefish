package com.idlefish.trade.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.mapper.AdminUserMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.system.entity.AdminUserRole;
import com.idlefish.trade.system.entity.RoleMenu;
import com.idlefish.trade.system.entity.SysMenu;
import com.idlefish.trade.system.mapper.AdminUserRoleMapper;
import com.idlefish.trade.system.mapper.RoleMenuMapper;
import com.idlefish.trade.system.mapper.SysMenuMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 权限服务（PC 系统管理 + 运营后台）：
 * - 新增基于 DB 的权限判定（管理员 → 角色 → 菜单 perms），为主路径；
 * - 保留静态 ROLE_PERMISSIONS 作为兜底（管理员未分配 DB 角色时，按遗留 role 字段校验），
 *   同时被既有运营接口 {@link #require(String, String)} 直接复用，避免改动已验证链路。
 */
@Service
public class AdminAuthService {

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = new HashMap<>();

    static {
        ROLE_PERMISSIONS.put("SUPER", new HashSet<>(Set.of(
                "item:audit", "item:manage", "order:view", "order:refund",
                "user:view", "user:ban", "category:manage", "risk:view", "audit:log")));
        ROLE_PERMISSIONS.put("OPERATOR", new HashSet<>(Set.of(
                "item:audit", "order:view", "user:view", "category:manage", "risk:view")));
        ROLE_PERMISSIONS.put("FINANCE", new HashSet<>(Set.of(
                "order:view", "order:refund", "audit:log")));
    }

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final SysMenuMapper sysMenuMapper;

    public AdminAuthService(AdminUserMapper adminUserMapper, AdminUserRoleMapper adminUserRoleMapper,
                            RoleMenuMapper roleMenuMapper, SysMenuMapper sysMenuMapper) {
        this.adminUserMapper = adminUserMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.sysMenuMapper = sysMenuMapper;
    }

    /** 静态角色权限校验（兜底 / 既有运营接口复用）。 */
    public void require(String role, String action) {
        Set<String> perms = ROLE_PERMISSIONS.get(role);
        if (perms == null || !perms.contains(action)) {
            throw new BizException(Code.FORBIDDEN, "角色 " + role + " 无权限 " + action);
        }
    }

    /**
     * 基于 DB 的权限判定（管理员 → 角色 → 菜单 perms）。
     * 管理员已分配 DB 角色时以 DB 为准；否则回退到遗留 role 字段的静态权限集合。
     */
    public boolean hasPermission(Long adminId, String perm) {
        List<Long> roleIds = adminUserRoleMapper.selectList(
                        new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getAdminUserId, adminId))
                .stream().map(AdminUserRole::getRoleId).collect(Collectors.toList());
        if (!roleIds.isEmpty()) {
            List<Long> menuIds = roleMenuMapper.selectList(
                            new LambdaQueryWrapper<RoleMenu>().in(RoleMenu::getRoleId, roleIds))
                    .stream().map(RoleMenu::getMenuId).collect(Collectors.toList());
            if (!menuIds.isEmpty()) {
                long cnt = sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                        .in(SysMenu::getId, menuIds)
                        .eq(SysMenu::getPerms, perm)
                        .eq(SysMenu::getStatus, 1));
                if (cnt > 0) {
                    return true;
                }
            }
        }
        // 回退：未按 DB 角色授权时，按遗留 role 字段做静态校验
        AdminUser u = adminUserMapper.selectById(adminId);
        if (u != null && u.getRole() != null) {
            Set<String> perms = ROLE_PERMISSIONS.get(u.getRole());
            return perms != null && perms.contains(perm);
        }
        return false;
    }

    /** 基于 DB 的权限校验，无权限抛异常。 */
    public void requirePermission(Long adminId, String perm) {
        if (!hasPermission(adminId, perm)) {
            throw new BizException(Code.FORBIDDEN, "无权限 " + perm);
        }
    }
}
