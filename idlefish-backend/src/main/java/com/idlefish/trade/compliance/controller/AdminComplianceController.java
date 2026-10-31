package com.idlefish.trade.compliance.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.compliance.ConsentItem;
import com.idlefish.trade.compliance.service.ComplianceService;
import com.idlefish.trade.compliance.vo.ComplianceProfileVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合规运营后台（ITER-U05）：未成年人标记/解除、用户合规档案查询。
 *
 * <p>标记来源为实名回执或人工审核结论；未成年人标记一旦生效，交易/支付/提现/线下见面
 * 立即受限（由 {@link ComplianceService#assertActionAllowed} 守卫）。
 */
@RestController
@RequestMapping("/api/admin/compliance")
public class AdminComplianceController {

    private final ComplianceService complianceService;

    public AdminComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    /** 标记未成年人身份（age < 18 自动置未成年；age 为空表示解除标记）。 */
    @PostMapping("/minor/mark")
    public Result<Void> markMinor(@CurrentAdmin AdminUser admin,
                                  @RequestParam Long userId,
                                  @RequestParam(required = false) Integer age) {
        complianceService.markMinor(userId, age);
        return Result.ok();
    }

    /** 查询指定用户合规档案（运营核查 / 申诉处理）。 */
    @GetMapping("/profile")
    public Result<ComplianceProfileVO> profile(@CurrentAdmin AdminUser admin, @RequestParam Long userId) {
        return Result.ok(complianceService.profile(userId));
    }

    /** 授权项字典（前端下拉与合规自查清单）。 */
    @GetMapping("/consent-items")
    public Result<ConsentItem[]> consentItems(@CurrentAdmin AdminUser admin) {
        return Result.ok(ConsentItem.values());
    }
}
