package com.idlefish.trade.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminService;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.risk.entity.AuditLog;
import com.idlefish.trade.risk.entity.RiskEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风控与审计（PRD §7）。
 * 风控事件列表 / 审计日志列表，均为只读查询，依赖 AdminService 统一分页与关键字检索。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminRiskAuditController {

    private final AdminService adminService;

    public AdminRiskAuditController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 风控事件列表，支持关键字（规则名/规则码/业务ID）搜索。 */
    @GetMapping("/risks")
    public Result<IPage<RiskEvent>> risks(@CurrentAdmin AdminUser admin,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.riskList(keyword, page, size));
    }

    /** 审计日志列表，支持关键字（动作/详情/目标类型）搜索。 */
    @GetMapping("/audit-logs")
    public Result<IPage<AuditLog>> auditLogs(@CurrentAdmin AdminUser admin,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.auditLogList(keyword, page, size));
    }
}
