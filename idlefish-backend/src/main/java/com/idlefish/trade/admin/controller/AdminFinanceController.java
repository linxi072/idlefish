package com.idlefish.trade.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.trade.entity.ReconcileBill;
import com.idlefish.trade.trade.entity.ReconcileDiff;
import com.idlefish.trade.trade.entity.Withdrawal;
import com.idlefish.trade.trade.mapper.ReconcileBillMapper;
import com.idlefish.trade.trade.mapper.ReconcileDiffMapper;
import com.idlefish.trade.trade.service.ReconciliationService;
import com.idlefish.trade.trade.service.SettlementService;
import com.idlefish.trade.trade.service.WithdrawalService;
import com.idlefish.trade.trade.vo.ReconcileBillDetailVO;
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
 * 资金与财务管理（PRD §F4 / §D7 / ITER-T02）。
 * 提现审批 / 资金对账 / 结算解冻 / 多维对账账单与差异工单下钻，所有写出账操作经 RBAC 权限校验与审计留痕。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminFinanceController {

    private final WithdrawalService withdrawalService;
    private final ReconciliationService reconciliationService;
    private final SettlementService settlementService;
    private final ReconcileBillMapper reconcileBillMapper;
    private final ReconcileDiffMapper reconcileDiffMapper;

    public AdminFinanceController(WithdrawalService withdrawalService,
                                 ReconciliationService reconciliationService,
                                 SettlementService settlementService,
                                 ReconcileBillMapper reconcileBillMapper,
                                 ReconcileDiffMapper reconcileDiffMapper) {
        this.withdrawalService = withdrawalService;
        this.reconciliationService = reconciliationService;
        this.settlementService = settlementService;
        this.reconcileBillMapper = reconcileBillMapper;
        this.reconcileDiffMapper = reconcileDiffMapper;
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

    /** 资金对账报告（默认昨日，单维 PAY，向后兼容）。 */
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

    /** 多维对账账单列表（ITER-T02）：按日期倒序、维度升序；支持按维度/日期筛选。 */
    @GetMapping("/reconcile-bills")
    public Result<List<ReconcileBill>> reconcileBills(@CurrentAdmin AdminUser admin,
                                                      @RequestParam(required = false) String dimension,
                                                      @RequestParam(required = false) String date) {
        LambdaQueryWrapper<ReconcileBill> w = new LambdaQueryWrapper<ReconcileBill>()
                .orderByDesc(ReconcileBill::getReconcileDate)
                .orderByAsc(ReconcileBill::getDimension);
        if (dimension != null && !dimension.isBlank()) {
            w.eq(ReconcileBill::getDimension, dimension);
        }
        if (date != null && !date.isBlank()) {
            try {
                w.eq(ReconcileBill::getReconcileDate, LocalDate.parse(date));
            } catch (Exception ignored) {
                // 非法日期忽略筛选
            }
        }
        return Result.ok(reconcileBillMapper.selectList(w));
    }

    /** 对账账单详情（ITER-T02 / REQ-07）：账单头 + 差异明细下钻。 */
    @GetMapping("/reconcile-bills/{id}")
    public Result<ReconcileBillDetailVO> reconcileBillDetail(@CurrentAdmin AdminUser admin,
                                                             @PathVariable Long id) {
        ReconcileBill bill = reconcileBillMapper.selectById(id);
        if (bill == null) {
            return Result.fail(Code.NOT_FOUND);
        }
        List<ReconcileDiff> diffs = reconcileDiffMapper.selectList(new LambdaQueryWrapper<ReconcileDiff>()
                .eq(ReconcileDiff::getBillId, id)
                .orderByAsc(ReconcileDiff::getId));
        ReconcileBillDetailVO vo = new ReconcileBillDetailVO();
        vo.setBill(bill);
        vo.setDiffs(diffs);
        return Result.ok(vo);
    }

    /** 差异明细列表（ITER-T02 / REQ-07）：按账单过滤。 */
    @GetMapping("/reconcile-diffs")
    public Result<List<ReconcileDiff>> reconcileDiffs(@CurrentAdmin AdminUser admin,
                                                      @RequestParam Long billId) {
        return Result.ok(reconcileDiffMapper.selectList(new LambdaQueryWrapper<ReconcileDiff>()
                .eq(ReconcileDiff::getBillId, billId)
                .orderByAsc(ReconcileDiff::getId)));
    }

    /**
     * 关闭对账账单（ITER-T02）：管理员确认处置后关闭，状态置 CLOSED 并留痕说明；
     * 同步将账单下未处置（OPEN）的差异工单关闭、记录处理人与说明（防二次资损，REQ-06）。
     */
    @PostMapping("/reconcile-bills/{id}/close")
    public Result<Void> closeReconcileBill(@CurrentAdmin AdminUser admin, @PathVariable Long id,
                                           @RequestParam(required = false) String note) {
        ReconcileBill bill = reconcileBillMapper.selectById(id);
        if (bill == null) {
            return Result.fail(Code.NOT_FOUND);
        }
        bill.setStatus("CLOSED");
        bill.setNote(note);
        reconcileBillMapper.updateById(bill);

        ReconcileDiff upd = new ReconcileDiff();
        upd.setStatus("CLOSED");
        upd.setHandlerId(admin.getId());
        upd.setHandleNote(note);
        reconcileDiffMapper.update(upd, new LambdaQueryWrapper<ReconcileDiff>()
                .eq(ReconcileDiff::getBillId, id)
                .eq(ReconcileDiff::getStatus, "OPEN"));
        return Result.ok();
    }
}
