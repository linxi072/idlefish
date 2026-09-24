package com.idlefish.trade.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminAuthService;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.system.entity.SysDictData;
import com.idlefish.trade.system.entity.SysDictType;
import com.idlefish.trade.system.service.SysDictService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据字典管理（PC 系统管理）：类型 CRUD + 明细 CRUD + 下拉接口（走缓存）。
 */
@RestController
@RequestMapping("/api/admin/system/dict")
public class SysDictController {

    private final SysDictService service;
    private final AdminAuthService authService;

    public SysDictController(SysDictService service, AdminAuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    // ===== 字典类型 =====

    @GetMapping("/types")
    public Result<IPage<SysDictType>> types(@CurrentAdmin AdminUser admin,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String keyword) {
        authService.requirePermission(admin.getId(), "system:dict:list");
        return Result.ok(service.listTypes(page, size, keyword));
    }

    @PostMapping("/type")
    public Result<Void> saveType(@CurrentAdmin AdminUser admin, @RequestBody SysDictType e) {
        authService.requirePermission(admin.getId(), "system:dict:edit");
        service.saveType(e);
        return Result.ok();
    }

    @PutMapping("/type/{id}")
    public Result<Void> updateType(@CurrentAdmin AdminUser admin, @PathVariable Long id, @RequestBody SysDictType e) {
        authService.requirePermission(admin.getId(), "system:dict:edit");
        e.setId(id);
        service.updateType(e);
        return Result.ok();
    }

    @DeleteMapping("/type/{id}")
    public Result<Void> removeType(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        authService.requirePermission(admin.getId(), "system:dict:edit");
        service.removeType(id);
        return Result.ok();
    }

    // ===== 字典明细 =====

    @GetMapping("/data")
    public Result<List<SysDictData>> data(@RequestParam String dictType) {
        return Result.ok(service.listData(dictType));
    }

    @PostMapping("/data")
    public Result<Void> saveData(@CurrentAdmin AdminUser admin, @RequestBody SysDictData e) {
        authService.requirePermission(admin.getId(), "system:dict:edit");
        service.saveData(e);
        return Result.ok();
    }

    @PutMapping("/data/{id}")
    public Result<Void> updateData(@CurrentAdmin AdminUser admin, @PathVariable Long id, @RequestBody SysDictData e) {
        authService.requirePermission(admin.getId(), "system:dict:edit");
        e.setId(id);
        service.updateData(e);
        return Result.ok();
    }

    @DeleteMapping("/data/{id}")
    public Result<Void> removeData(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        authService.requirePermission(admin.getId(), "system:dict:edit");
        service.removeData(id);
        return Result.ok();
    }

    // ===== 下拉（公开给后台前端，供 select 使用，走缓存） =====

    @GetMapping("/dropdown")
    public Result<List<Map<String, Object>>> dropdown(@RequestParam String dictType) {
        return Result.ok(service.dropdown(dictType));
    }
}
