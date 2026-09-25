package com.idlefish.trade.marketing.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.marketing.dto.CouponCreateDTO;
import com.idlefish.trade.marketing.entity.Coupon;
import com.idlefish.trade.marketing.service.CouponService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠券运营后台接口（PC 运营使用）：创建发券 / 启停。与既有 /api/admin/* 同域受同一鉴权。
 */
@RestController
@RequestMapping("/api/admin/coupon")
public class CouponAdminController {

    private final CouponService couponService;

    public CouponAdminController(CouponService couponService) {
        this.couponService = couponService;
    }

    /** 创建优惠券（发放）。 */
    @PostMapping("/create")
    public Result<Coupon> create(@RequestBody CouponCreateDTO dto) {
        return Result.ok(couponService.createCoupon(dto));
    }

    /** 启停 / 结束：status = ACTIVE / PAUSED / ENDED。 */
    @PostMapping("/status")
    public Result<Boolean> status(@RequestParam Long couponId, @RequestParam String status) {
        couponService.updateStatus(couponId, status);
        return Result.ok(true);
    }
}
