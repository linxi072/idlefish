package com.idlefish.trade.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.mapper.AdminUserMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.util.PasswordUtils;
import com.idlefish.trade.system.entity.AdminUserRole;
import com.idlefish.trade.system.mapper.AdminUserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 后台用户（管理员）服务（PC 系统管理）：管理员 CRUD + 管理员↔角色分配 + 密码重置。
 * 密码以 PasswordUtils（PBKDF2 加盐）哈希存储。
 */
@Service
public class SysUserService {

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;

    public SysUserService(AdminUserMapper adminUserMapper, AdminUserRoleMapper adminUserRoleMapper) {
        this.adminUserMapper = adminUserMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
    }

    public IPage<AdminUser> list(int page, int size, String keyword, Long orgId, Integer status) {
        LambdaQueryWrapper<AdminUser> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(AdminUser::getUsername, keyword).or().like(AdminUser::getNickname, keyword);
        }
        if (orgId != null) {
            w.eq(AdminUser::getOrgId, orgId);
        }
        if (status != null) {
            w.eq(AdminUser::getStatus, status);
        }
        w.orderByDesc(AdminUser::getId);
        return adminUserMapper.selectPage(new Page<>(page, size), w);
    }

    public AdminUser get(Long id) {
        return adminUserMapper.selectById(id);
    }

    public void save(AdminUser e) {
        long cnt = adminUserMapper.selectCount(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, e.getUsername()));
        if (cnt > 0) {
            throw new BizException(Code.BIZ_ERROR, "用户名已存在");
        }
        if (e.getPassword() != null && !e.getPassword().isBlank()) {
            e.setPassword(PasswordUtils.hash(e.getPassword()));
        }
        adminUserMapper.insert(e);
    }

    public void update(AdminUser e) {
        if (e.getPassword() != null && !e.getPassword().isBlank()) {
            e.setPassword(PasswordUtils.hash(e.getPassword()));
        }
        adminUserMapper.updateById(e);
    }

    public void remove(Long id) {
        adminUserRoleMapper.delete(new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getAdminUserId, id));
        adminUserMapper.deleteById(id);
    }

    /** 重新分配管理员的角色（先清后插，保证幂等）。 */
    public void assignRoles(Long adminUserId, List<Long> roleIds) {
        adminUserRoleMapper.delete(new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getAdminUserId, adminUserId));
        for (Long roleId : roleIds) {
            AdminUserRole r = new AdminUserRole();
            r.setAdminUserId(adminUserId);
            r.setRoleId(roleId);
            adminUserRoleMapper.insert(r);
        }
    }

    public List<Long> userRoles(Long adminUserId) {
        return adminUserRoleMapper.selectList(new LambdaQueryWrapper<AdminUserRole>()
                        .eq(AdminUserRole::getAdminUserId, adminUserId))
                .stream().map(AdminUserRole::getRoleId).collect(Collectors.toList());
    }

    public void resetPassword(Long adminUserId, String newPassword) {
        AdminUser u = adminUserMapper.selectById(adminUserId);
        if (u == null) {
            throw new BizException(Code.NOT_FOUND, "管理员不存在");
        }
        u.setPassword(PasswordUtils.hash(newPassword));
        adminUserMapper.updateById(u);
    }
}
