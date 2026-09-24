package com.idlefish.trade.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.system.entity.AdminUserRole;
import com.idlefish.trade.system.entity.RoleMenu;
import com.idlefish.trade.system.entity.SysRole;
import com.idlefish.trade.system.mapper.AdminUserRoleMapper;
import com.idlefish.trade.system.mapper.RoleMenuMapper;
import com.idlefish.trade.system.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务（PC 系统管理）：角色 CRUD + 角色↔菜单权限分配。
 * 权限由关联的菜单 perms 决定（见 AdminAuthService.hasPermission）。
 */
@Service
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;

    public SysRoleService(SysRoleMapper roleMapper, RoleMenuMapper roleMenuMapper,
                          AdminUserRoleMapper adminUserRoleMapper) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
    }

    public IPage<SysRole> list(int page, int size, String keyword) {
        LambdaQueryWrapper<SysRole> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(SysRole::getName, keyword).or().like(SysRole::getCode, keyword);
        }
        w.orderByAsc(SysRole::getSort);
        return roleMapper.selectPage(new Page<>(page, size), w);
    }

    public SysRole get(Long id) {
        return roleMapper.selectById(id);
    }

    public void save(SysRole e) {
        roleMapper.insert(e);
    }

    public void update(SysRole e) {
        roleMapper.updateById(e);
    }

    public void remove(Long id) {
        // 级联清理：角色-菜单、管理员-角色
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, id));
        adminUserRoleMapper.delete(new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getRoleId, id));
        roleMapper.deleteById(id);
    }

    /** 重新分配角色的菜单权限（先清后插，保证幂等）。 */
    public void assignMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        for (Long menuId : menuIds) {
            RoleMenu rm = new RoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
    }

    public List<Long> roleMenus(Long roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId))
                .stream().map(RoleMenu::getMenuId).collect(Collectors.toList());
    }
}
