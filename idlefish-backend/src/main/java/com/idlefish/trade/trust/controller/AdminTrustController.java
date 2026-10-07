package com.idlefish.trade.trust.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.trust.service.TrustViewService;
import com.idlefish.trade.trust.vo.TrustItemView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 信任体系可视化运营后台（U02）：订单/工单管理页同步展示验货与信用字段，便于客服判断。
 */
@RestController
@RequestMapping("/api/admin/trust")
public class AdminTrustController {

    private final TrustViewService trustViewService;

    public AdminTrustController(TrustViewService trustViewService) {
        this.trustViewService = trustViewService;
    }

    /** PC 后台商品信任视图（验货标 + 信用摘要）。 */
    @GetMapping("/item")
    public Result<TrustItemView> item(@CurrentAdmin AdminUser admin, @RequestParam Long itemId) {
        return Result.ok(trustViewService.itemView(itemId));
    }
}
