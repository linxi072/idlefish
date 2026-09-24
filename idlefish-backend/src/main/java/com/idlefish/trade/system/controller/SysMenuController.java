package com.idlefish.trade.system.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminAuthService;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.vo.TreeVO;
import com.idlefish.trade.system.entity.SysMenu;
import com.idlefish.trade.system.service.SysMenuService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单/权限管理（PC 系统管理）：菜单树 CRUD。
 */
@RestController
@RequestMapping("/api/admin/system/menu")
public class SysMenuController {

    private final SysMenuService service;
    private final AdminAuthService authService;

    public SysMenuController(SysMenuService service, AdminAuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping("/tree")
    public Result<List<TreeVO>> tree() {
        return Result.ok(service.tree());
    }

    @GetMapping
    public Result<List<SysMenu>> list() {
        return Result.ok(service.list());
    }

    @GetMapping("/{id}")
    public Result<SysMenu> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    @PostMapping
    public Result<Void> save(@CurrentAdmin AdminUser admin, @RequestBody SysMenu e) {
        authService.requirePermission(admin.getId(), "system:menu:edit");
        service.save(e);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@CurrentAdmin AdminUser admin, @PathVariable Long id, @RequestBody SysMenu e) {
        authService.requirePermission(admin.getId(), "system:menu:edit");
        e.setId(id);
        service.update(e);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        authService.requirePermission(admin.getId(), "system:menu:edit");
        service.remove(id);
        return Result.ok();
    }
}
