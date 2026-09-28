package com.idlefish.trade.dispute.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.dispute.entity.Dispute;
import com.idlefish.trade.dispute.service.DisputeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 售后维权工单运营后台（F-17）：工单分页 / 详情 / 平台裁决 / 归档关闭。
 *
 * <p>裁决仅落库结果与裁决退款金额，实际退款由既有退款链路执行，避免重复资金操作。
 */
@RestController
@RequestMapping("/api/admin/dispute")
public class AdminDisputeController {

    private final DisputeService disputeService;

    public AdminDisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    /** 工单分页（status 可选；keyword 匹配工单号/订单号）。 */
    @GetMapping("/list")
    public Result<IPage<Dispute>> list(@CurrentAdmin AdminUser admin,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(disputeService.adminPage(status, keyword, page, size));
    }

    /** 工单详情。 */
    @GetMapping("/detail")
    public Result<Dispute> detail(@CurrentAdmin AdminUser admin, @RequestParam Long id) {
        return Result.ok(disputeService.adminDetail(id));
    }

    /** 平台裁决（落库裁决结果与退款金额，不直接出款）。 */
    @PostMapping("/resolve")
    public Result<Void> resolve(@CurrentAdmin AdminUser admin,
                                @RequestParam Long id,
                                @RequestParam String result,
                                @RequestParam(required = false) Long refundAmount,
                                @RequestParam(required = false) String remark) {
        disputeService.resolve(id, result, refundAmount, remark);
        return Result.ok();
    }

    /** 归档关闭。 */
    @PostMapping("/close")
    public Result<Void> close(@CurrentAdmin AdminUser admin, @RequestParam Long id) {
        disputeService.close(id);
        return Result.ok();
    }
}
