package com.idlefish.trade.marketing.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.marketing.service.AdminCouponAnalyticsService;
import com.idlefish.trade.marketing.vo.CouponAnalyticsOverview;
import com.idlefish.trade.marketing.vo.CouponTypeDist;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 券营销驾驶舱接口（F-13.5）。
 * 概览（发放量 / 核销率 / 核销 GMV / 优惠成本 / ROI）+ 券类型分布；所有读接口经 RBAC 鉴权。
 */
@RestController
@RequestMapping("/api/admin/marketing/coupon")
public class AdminCouponAnalyticsController {

    private final AdminCouponAnalyticsService couponAnalyticsService;

    public AdminCouponAnalyticsController(AdminCouponAnalyticsService couponAnalyticsService) {
        this.couponAnalyticsService = couponAnalyticsService;
    }

    /** 券营销概览。 */
    @GetMapping("/overview")
    public Result<CouponAnalyticsOverview> overview(@CurrentAdmin AdminUser admin) {
        return Result.ok(couponAnalyticsService.overview());
    }

    /** 券类型发放分布。 */
    @GetMapping("/type-dist")
    public Result<List<CouponTypeDist>> typeDist(@CurrentAdmin AdminUser admin) {
        return Result.ok(couponAnalyticsService.typeDistribution());
    }
}
