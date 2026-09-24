package com.idlefish.trade.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminAuthService;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.system.dto.AssignDTO;
import com.idlefish.trade.system.service.SysUserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台用户（管理员）管理（PC 系统管理）：管理员 CRUD + 角色分配 + 密码重置。
 * 所有写操作经 RBAC 权限校验（system:user:add/edit/delete）。
 */
@RestController
@RequestMapping("/api/admin/system/user")
public class SysUserController {

    private final SysUserService service;
    private final AdminAuthService authService;

    public SysUserController(SysUserService service, AdminAuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public Result<IPage<AdminUser>> list(@CurrentAdmin AdminUser admin,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Long orgId,
                                        @RequestParam(required = false) Integer status) {
        authService.requirePermission(admin.getId(), "system:user:list");
        return Result.ok(service.list(page, size, keyword, orgId, status));
    }

    @GetMapping("/{id}")
    public Result<AdminUser> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    @PostMapping
    public Result<Void> save(@CurrentAdmin AdminUser admin, @RequestBody AdminUser e) {
        authService.requirePermission(admin.getId(), "system:user:add");
        service.save(e);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@CurrentAdmin AdminUser admin, @PathVariable Long id, @RequestBody AdminUser e) {
        authService.requirePermission(admin.getId(), "system:user:edit");
        e.setId(id);
        service.update(e);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        authService.requirePermission(admin.getId(), "system:user:delete");
        service.remove(id);
        return Result.ok();
    }

    /** 分配管理员角色（按钮级 perm：system:user:edit）。 */
    @PostMapping("/assign-roles")
    public Result<Void> assignRoles(@CurrentAdmin AdminUser admin, @RequestBody AssignDTO dto) {
        authService.requirePermission(admin.getId(), "system:user:edit");
        service.assignRoles(dto.getId(), dto.getIds());
        return Result.ok();
    }

    @GetMapping("/{adminUserId}/roles")
    public Result<List<Long>> userRoles(@PathVariable Long adminUserId) {
        return Result.ok(service.userRoles(adminUserId));
    }

    /** 重置管理员密码（按钮级 perm：system:user:edit）。 */
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@CurrentAdmin AdminUser admin, @PathVariable Long id,
                                      @RequestParam String password) {
        authService.requirePermission(admin.getId(), "system:user:edit");
        service.resetPassword(id, password);
        return Result.ok();
    }
}
