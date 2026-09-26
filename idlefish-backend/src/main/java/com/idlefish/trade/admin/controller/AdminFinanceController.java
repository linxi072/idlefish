package com.idlefish.trade.admin.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.trade.entity.Withdrawal;
import com.idlefish.trade.trade.service.ReconciliationService;
import com.idlefish.trade.trade.service.SettlementService;
import com.idlefish.trade.trade.service.WithdrawalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 资金与财务管理（PRD §F4 / §D7）。
 * 提现审批 / 资金对账 / 结算解冻，所有写出账操作经 RBAC 权限校验与审计留痕。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminFinanceController {

    private final WithdrawalService withdrawalService;
    private final ReconciliationService reconciliationService;
    private final SettlementService settlementService;

    public AdminFinanceController(WithdrawalService withdrawalService,
                                 ReconciliationService reconciliationService,
                                 SettlementService settlementService) {
        this.withdrawalService = withdrawalService;
        this.reconciliationService = reconciliationService;
        this.settlementService = settlementService;
    }

    /** 提现申请列表。 */
    @GetMapping("/withdrawals")
    public Result<List<Withdrawal>> withdrawals(@CurrentAdmin AdminUser admin) {
        return Result.ok(withdrawalService.list());
    }

    /** 审批通过提现（实际出账）。 */
    @PostMapping("/withdrawals/{id}/approve")
    public Result<Void> approveWithdrawal(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        withdrawalService.approve(id);
        return Result.ok();
    }

    /** 驳回提现（解冻）。 */
    @PostMapping("/withdrawals/{id}/reject")
    public Result<Void> rejectWithdrawal(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        withdrawalService.reject(id);
        return Result.ok();
    }

    /** 资金对账报告（默认昨日）。 */
    @GetMapping("/reconciliation")
    public Result<Map<String, Object>> reconciliation(@CurrentAdmin AdminUser admin,
                                                     @RequestParam(required = false) String day) {
        LocalDate d = null;
        if (day != null && !day.isBlank()) {
            try {
                d = LocalDate.parse(day);
            } catch (Exception ignored) {
                d = null;
            }
        }
        return Result.ok(reconciliationService.reconcile(d));
    }

    /** 解冻被风控冻结的结算单。 */
    @PostMapping("/settlements/{id}/unfreeze")
    public Result<Void> unfreezeSettlement(@CurrentAdmin AdminUser admin, @PathVariable Long id) {
        settlementService.unfreeze(id);
        return Result.ok();
    }
}
