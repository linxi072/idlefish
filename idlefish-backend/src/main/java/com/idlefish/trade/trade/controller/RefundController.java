package com.idlefish.trade.trade.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.trade.dto.RefundApplyDTO;
import com.idlefish.trade.trade.service.RefundService;
import com.idlefish.trade.trade.vo.RefundVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 退款接口（PRD §4.4）：仅退款 / 退货退款，卖家处理、买家退货、平台介入、撤销。
 */
@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    /** 申请退款（买家）。 */
    @PostMapping("/apply")
    public Result<String> apply(@CurrentUser LoginUser loginUser, @RequestBody RefundApplyDTO dto) {
        return Result.ok(refundService.apply(loginUser.getUserId(), dto));
    }

    /** 卖家同意退款。 */
    @PostMapping("/{refundNo}/agree")
    public Result<Void> agree(@CurrentUser LoginUser loginUser, @PathVariable String refundNo) {
        refundService.agree(loginUser.getUserId(), refundNo);
        return Result.ok();
    }

    /** 卖家拒绝退款。 */
    @PostMapping("/{refundNo}/reject")
    public Result<Void> reject(@CurrentUser LoginUser loginUser, @PathVariable String refundNo,
                               @RequestParam String reason) {
        refundService.reject(loginUser.getUserId(), refundNo, reason);
        return Result.ok();
    }

    /** 买家填写退货物流（退货退款）。 */
    @PostMapping("/{refundNo}/return-logistics")
    public Result<Void> returnLogistics(@CurrentUser LoginUser loginUser, @PathVariable String refundNo,
                                        @RequestParam String logisticsNo) {
        refundService.returnLogistics(loginUser.getUserId(), refundNo, logisticsNo);
        return Result.ok();
    }

    /** 卖家确认收货并退款（退货退款）。 */
    @PostMapping("/{refundNo}/confirm-return")
    public Result<Void> confirmReturn(@CurrentUser LoginUser loginUser, @PathVariable String refundNo) {
        refundService.confirmReturn(loginUser.getUserId(), refundNo);
        return Result.ok();
    }

    /** 平台介入。 */
    @PostMapping("/platform/{refundNo}")
    public Result<Void> platform(@PathVariable String refundNo) {
        refundService.platformIntervene(refundNo);
        return Result.ok();
    }

    /** 买家撤销退款。 */
    @PostMapping("/{refundNo}/cancel")
    public Result<Void> cancel(@CurrentUser LoginUser loginUser, @PathVariable String refundNo) {
        refundService.cancel(loginUser.getUserId(), refundNo);
        return Result.ok();
    }

    /** 退款详情。 */
    @GetMapping("/{refundNo}")
    public Result<RefundVO> detail(@PathVariable String refundNo) {
        return Result.ok(refundService.detail(refundNo));
    }

    /** 某订单的退款单列表。 */
    @GetMapping("/order/{orderNo}")
    public Result<List<RefundVO>> listByOrder(@PathVariable String orderNo) {
        return Result.ok(refundService.listByOrder(orderNo));
    }
}
