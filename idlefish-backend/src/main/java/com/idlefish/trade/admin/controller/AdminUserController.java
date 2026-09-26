package com.idlefish.trade.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminService;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.user.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理（PRD §7）。
 * 用户列表 / 封禁解封，所有写操作经 RBAC 权限校验与审计留痕（由 AdminService 内部完成）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final AdminService adminService;

    public AdminUserController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 用户列表，支持状态过滤与关键字（昵称/手机号）搜索。 */
    @GetMapping("/users")
    public Result<IPage<User>> users(@CurrentAdmin AdminUser admin,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.userList(status, keyword, page, size));
    }

    /** 封禁 / 解封用户（status=1 封禁，0 解封）。 */
    @PostMapping("/users/{userId}/ban")
    public Result<Void> ban(@CurrentAdmin AdminUser admin, @PathVariable Long userId,
                            @RequestParam Integer status) {
        adminService.banUser(admin.getRole(), admin.getId(), userId, status);
        return Result.ok();
    }
}
