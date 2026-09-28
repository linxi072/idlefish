package com.idlefish.trade.dispute.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.dispute.entity.Dispute;
import com.idlefish.trade.dispute.service.DisputeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 售后维权工单用户端接口（F-17，需登录）：发起 / 举证 / 申请平台介入 / 撤销 / 查询。
 */
@RestController
@RequestMapping("/api/dispute")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    /** 发起维权（买家）。 */
    @PostMapping("/create")
    public Result<Long> create(@CurrentUser LoginUser user,
                               @RequestParam String orderNo,
                               @RequestParam String type,
                               @RequestParam String expectation,
                               @RequestParam(required = false) String reason,
                               @RequestParam(required = false) Long amount,
                               @RequestParam(required = false) String buyerEvidence) {
        return Result.ok(disputeService.create(user.getUserId(), orderNo, type, expectation,
                reason, amount, buyerEvidence));
    }

    /** 卖家举证。 */
    @PostMapping("/seller-reply")
    public Result<Void> sellerReply(@CurrentUser LoginUser user,
                                    @RequestParam Long id,
                                    @RequestParam(required = false) String evidence) {
        disputeService.sellerReply(id, user.getUserId(), evidence);
        return Result.ok();
    }

    /** 申请平台介入（买卖双方均可）。 */
    @PostMapping("/apply-platform")
    public Result<Void> applyPlatform(@CurrentUser LoginUser user, @RequestParam Long id) {
        disputeService.applyPlatform(id, user.getUserId());
        return Result.ok();
    }

    /** 撤销维权（仅买家，平台介入后不可撤销）。 */
    @PostMapping("/cancel")
    public Result<Void> cancel(@CurrentUser LoginUser user, @RequestParam Long id) {
        disputeService.cancel(id, user.getUserId());
        return Result.ok();
    }

    /** 我的维权工单（买家或卖家视角，分页）。 */
    @GetMapping("/my")
    public Result<IPage<Dispute>> my(@CurrentUser LoginUser user,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(disputeService.myList(user.getUserId(), page, size));
    }

    /** 工单详情（仅买卖双方可见）。 */
    @GetMapping("/detail")
    public Result<Dispute> detail(@CurrentUser LoginUser user, @RequestParam Long id) {
        return Result.ok(disputeService.detail(id, user.getUserId()));
    }
}
