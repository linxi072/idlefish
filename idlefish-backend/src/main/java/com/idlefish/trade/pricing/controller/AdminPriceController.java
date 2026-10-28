package com.idlefish.trade.pricing.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.pricing.dto.PriceConfigRequest;
import com.idlefish.trade.pricing.dto.PriceOverrideRequest;
import com.idlefish.trade.pricing.entity.PriceEstimateAudit;
import com.idlefish.trade.pricing.entity.PriceEstimateConfig;
import com.idlefish.trade.pricing.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 估价运营后台（ITER-F03）：品类配置 / 系数覆盖留痕 / 异常低价列表 / 重算 / 审计日志。
 *
 * <p>权限：均需管理员登录（@CurrentAdmin）；系数覆盖与配置变更经 {@link PricingService} 落审计表（REQ-08）。
 */
@RestController
@RequestMapping("/api/admin/price")
public class AdminPriceController {

    private final PricingService pricingService;

    public AdminPriceController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    /** 读取品类估价配置（无则 null）。 */
    @GetMapping("/config")
    public Result<PriceEstimateConfig> config(@CurrentAdmin AdminUser admin,
                                             @RequestParam Long categoryId) {
        return Result.ok(pricingService.getConfig(categoryId));
    }

    /** REQ-08 保存/更新品类估价配置（基准价/系数/状态）。 */
    @PostMapping("/config")
    public Result<Void> saveConfig(@CurrentAdmin AdminUser admin,
                                  @Valid @RequestBody PriceConfigRequest req) {
        pricingService.saveConfig(req.getCategoryId(), req.getBasePriceFen(),
                req.getMultiplier(), req.getStatus(), admin.getId());
        return Result.ok();
    }

    /** REQ-08 运营系数覆盖（在既有配置上调整并留痕）。 */
    @PostMapping("/override")
    public Result<Void> override(@CurrentAdmin AdminUser admin,
                                @Valid @RequestBody PriceOverrideRequest req) {
        pricingService.override(req.getCategoryId(), req.getMultiplier(), req.getReason(), admin.getId());
        return Result.ok();
    }

    /** REQ-10 异常低价送审列表（风控关注）。 */
    @GetMapping("/abnormal")
    public Result<List<PriceEstimateAudit>> abnormal(@CurrentAdmin AdminUser admin) {
        return Result.ok(pricingService.abnormalList());
    }

    /** REQ-05 校准：基于样本重算品类基准价（categoryId 可空=全量）。 */
    @PostMapping("/recalibrate")
    public Result<Void> recalibrate(@CurrentAdmin AdminUser admin,
                                    @RequestParam(required = false) Long categoryId) {
        pricingService.recalibrate(categoryId);
        return Result.ok();
    }

    /** REQ-08 估价操作审计日志（系数覆盖/配置/重算留痕）。 */
    @GetMapping("/audit-log")
    public Result<List<PriceEstimateAudit>> auditLog(@CurrentAdmin AdminUser admin) {
        return Result.ok(pricingService.auditLog());
    }
}
