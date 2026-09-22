package com.idlefish.trade.trade.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.trade.service.PayService;
import com.idlefish.trade.trade.service.PrepayResult;
import org.springframework.web.bind.annotation.*;

/**
 * 支付接口（资金托管为接口抽象，本地以 Mock 实现；真实对接见 RealWechatEscrowServiceImpl）。
 * 支付结果通知通常由微信回调，需放行鉴权。
 */
@RestController
@RequestMapping("/api/pay")
public class PayController {

    private final PayService payService;

    public PayController(PayService payService) {
        this.payService = payService;
    }

    /** 预下单：返回微信支付参数（演示为 mock prepay_id）。 */
    @PostMapping("/prepay")
    public Result<PrepayResult> prepay(@RequestParam String orderNo) {
        return Result.ok(payService.prepay(orderNo));
    }

    /**
     * 支付结果通知（幂等 + 防伪造）。演示环境由前端/脚本触发（可传 amount 校验）；
     * 真实环境由微信异步回调，验签在 PayService 内完成。
     */
    @PostMapping("/notify")
    public Result<Void> notify(@RequestParam String payNo,
                               @RequestParam(required = false) String transactionId,
                               @RequestParam(required = false) Long amount) {
        payService.notify(payNo, transactionId, amount);
        return Result.ok();
    }

    /** Mock 支付完成（仅 Mock 模式可用）：演示链路中由前端触发，走通 待支付→已支付。 */
    @PostMapping("/mock/{payNo}")
    public Result<Void> mockPay(@PathVariable String payNo) {
        payService.mockComplete(payNo);
        return Result.ok();
    }
}
