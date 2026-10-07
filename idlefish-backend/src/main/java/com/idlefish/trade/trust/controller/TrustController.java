package com.idlefish.trade.trust.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.trust.service.TrustViewService;
import com.idlefish.trade.trust.vo.GuaranteeTerms;
import com.idlefish.trade.trust.vo.TrustItemView;
import com.idlefish.trade.trust.vo.TrustOrderView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 信任体系可视化用户端接口（U02）：商品详情信任条 / 订单信任视图 / 保障条款。
 * 信任信息植入买家决策路径（详情页首屏、下单确认页、订单详情）。
 */
@RestController
@RequestMapping("/api/trust")
public class TrustController {

    private final TrustViewService trustViewService;

    public TrustController(TrustViewService trustViewService) {
        this.trustViewService = trustViewService;
    }

    /** REQ-01/02/03/08/09/10/11 商品详情信任条。 */
    @GetMapping("/item")
    public Result<TrustItemView> item(@CurrentUser LoginUser user, @RequestParam Long itemId) {
        return Result.ok(trustViewService.itemView(itemId));
    }

    /** REQ-05/07 + 保障摘要：订单详情信任视图。 */
    @GetMapping("/order")
    public Result<TrustOrderView> order(@CurrentUser LoginUser user, @RequestParam String orderNo) {
        return Result.ok(trustViewService.orderView(orderNo));
    }

    /** REQ-04 完整保障条款（可展开）。 */
    @GetMapping("/guarantee-terms")
    public Result<GuaranteeTerms> guaranteeTerms() {
        return Result.ok(trustViewService.guaranteeTerms());
    }
}
