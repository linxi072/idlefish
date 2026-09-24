package com.idlefish.trade.system.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminAuthService;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.vo.TreeVO;
import com.idlefish.trade.system.entity.SysOrganization;
import com.idlefish.trade.system.service.SysOrganizationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 机构/部门管理（PC 系统管理）。所有写操作经 RBAC 权限校验。
 */
@RestController
@RequestMapping("/api/admin/system/organization")
public class SysOrganizationController {

    private final SysOrganizationService service;
    private final AdminAuthService authService;

    public SysOrganizationController(SysOrganizationService service, AdminAuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping("/tree")
    public Result<List<TreeVO>> tree() {
        return Result.ok(service.tree());
    }

    @GetMapping
    public Result<List<SysOrganization>> list() {
        return Result.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public Result<SysOrganization> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    @PostMapping
    public Result<Void> save(@CurrentAdmin AdminUser admin, @RequestBody SysOrganization e) {
        authService.requirePermission(admin.getId(), "system:org:edit");
        service.save(e);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@CurrentAdmin AdminUser admin, @PathVariable Long id, @RequestBody SysOrganization e) {
        authService.requirePermission(admin.getId(), "system:org:edit");
        e.setId(id);
        service.update(e);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        authService.requirePermission(admin.getId(), "system:org:edit");
        service.remove(id);
        return Result.ok();
    }
}
