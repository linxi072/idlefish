package com.idlefish.trade.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminService;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.item.entity.Item;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品内容审核（PRD §7）。
 * 审核列表 / 通过 / 驳回，所有写操作经 RBAC 权限校验与审计留痕（由 AdminService 内部完成）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminItemAuditController {

    private final AdminService adminService;

    public AdminItemAuditController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 审核列表（默认待审核），支持状态过滤与关键字（标题）搜索。 */
    @GetMapping("/items")
    public Result<IPage<Item>> items(@CurrentAdmin AdminUser admin,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.auditList(status, keyword, page, size));
    }

    /** 通过审核。 */
    @PostMapping("/items/{itemId}/approve")
    public Result<Void> approve(@CurrentAdmin AdminUser admin, @PathVariable Long itemId) {
        adminService.approve(admin.getRole(), admin.getId(), itemId);
        return Result.ok();
    }

    /** 驳回审核。 */
    @PostMapping("/items/{itemId}/reject")
    public Result<Void> reject(@CurrentAdmin AdminUser admin, @PathVariable Long itemId,
                               @RequestParam String reason) {
        adminService.reject(admin.getRole(), admin.getId(), itemId, reason);
        return Result.ok();
    }
}
