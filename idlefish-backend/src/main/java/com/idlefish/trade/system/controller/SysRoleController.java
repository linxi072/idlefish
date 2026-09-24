package com.idlefish.trade.system.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminAuthService;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.system.dto.AssignDTO;
import com.idlefish.trade.system.entity.SysRole;
import com.idlefish.trade.system.service.SysRoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理（PC 系统管理）：角色 CRUD + 角色分配菜单权限。
 */
@RestController
@RequestMapping("/api/admin/system/role")
public class SysRoleController {

    private final SysRoleService service;
    private final AdminAuthService authService;

    public SysRoleController(SysRoleService service, AdminAuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public Result<?> list(@CurrentAdmin AdminUser admin,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) String keyword) {
        authService.requirePermission(admin.getId(), "system:role:list");
        return Result.ok(service.list(page, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<SysRole> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    @PostMapping
    public Result<Void> save(@CurrentAdmin AdminUser admin, @RequestBody SysRole e) {
        authService.requirePermission(admin.getId(), "system:role:edit");
        service.save(e);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@CurrentAdmin AdminUser admin, @PathVariable Long id, @RequestBody SysRole e) {
        authService.requirePermission(admin.getId(), "system:role:edit");
        e.setId(id);
        service.update(e);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        authService.requirePermission(admin.getId(), "system:role:edit");
        service.remove(id);
        return Result.ok();
    }

    /** 分配角色的菜单权限（按钮级 perm：system:role:assign）。 */
    @PostMapping("/assign-menus")
    public Result<Void> assignMenus(@CurrentAdmin AdminUser admin, @RequestBody AssignDTO dto) {
        authService.requirePermission(admin.getId(), "system:role:assign");
        service.assignMenus(dto.getId(), dto.getIds());
        return Result.ok();
    }

    @GetMapping("/{roleId}/menus")
    public Result<List<Long>> roleMenus(@PathVariable Long roleId) {
        return Result.ok(service.roleMenus(roleId));
    }
}
