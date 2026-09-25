package com.idlefish.trade.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.marketing.service.CouponService;
import com.idlefish.trade.marketing.vo.CouponVO;
import com.idlefish.trade.marketing.vo.UserCouponVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 优惠券用户端接口（需登录）：领券中心 / 领取 / 我的券 / 下单可用券。
 */
@RestController
@RequestMapping("/api/coupon")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /** 领券中心（分页，仅展示可领券）。 */
    @GetMapping("/center")
    public Result<IPage<CouponVO>> center(@CurrentUser LoginUser user,
                                          @RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(couponService.pageCenter(page, size, user.getUserId()));
    }

    /** 领取优惠券。 */
    @PostMapping("/claim")
    public Result<Boolean> claim(@CurrentUser LoginUser user, @RequestParam Long couponId) {
        couponService.claim(user.getUserId(), couponId);
        return Result.ok(true);
    }

    /** 我的优惠券（status 可选：UNUSED/USED/EXPIRED）。 */
    @GetMapping("/my")
    public Result<List<UserCouponVO>> my(@CurrentUser LoginUser user,
                                        @RequestParam(required = false) String status) {
        return Result.ok(couponService.myCoupons(user.getUserId(), status));
    }

    /** 下单可用券（itemId + 商品金额（分））。 */
    @GetMapping("/available")
    public Result<List<UserCouponVO>> available(@CurrentUser LoginUser user,
                                               @RequestParam Long itemId,
                                               @RequestParam Long amount) {
        return Result.ok(couponService.availableCoupons(user.getUserId(), itemId, amount));
    }
}
