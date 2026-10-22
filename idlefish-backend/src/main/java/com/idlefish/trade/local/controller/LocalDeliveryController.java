package com.idlefish.trade.local.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.local.dto.LocalDeliveryApplyRequest;
import com.idlefish.trade.local.entity.LocalDelivery;
import com.idlefish.trade.local.service.LocalDeliveryService;
import com.idlefish.trade.local.vo.LocalAvailabilityVO;
import com.idlefish.trade.local.vo.LocalDeliveryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 同城配送用户端接口（ITER-F05，需登录）：可用性预检 / 运费预估 / 下单 / 取消 / 轨迹 / 详情。
 *
 * <p>REQ-09：服务商不可用时预检返回 available=false + 原因；下单与轨迹查询在失败时
 * 返回明确的 {@code LOCAL_DELIVERY_UNAVAILABLE}，不返回空数组伪造成功。
 */
@RestController
@RequestMapping("/api/local/delivery")
public class LocalDeliveryController {

    private final LocalDeliveryService localDeliveryService;

    public LocalDeliveryController(LocalDeliveryService localDeliveryService) {
        this.localDeliveryService = localDeliveryService;
    }

    /** REQ-09 同城配送可用性预检（城市 / 距离 / 服务商接入）。 */
    @PostMapping("/check")
    public Result<LocalAvailabilityVO> check(@CurrentUser LoginUser user,
                                             @RequestParam String city,
                                             @RequestParam(required = false) Double distanceKm) {
        return Result.ok(localDeliveryService.check(city, distanceKm));
    }

    /** REQ-07 运费预估（分）。 */
    @PostMapping("/freight")
    public Result<Long> freight(@CurrentUser LoginUser user,
                                @RequestParam(required = false) Double distanceKm) {
        return Result.ok(localDeliveryService.freight(distanceKm));
    }

    /** REQ-06 创建同城配送单（第三方调度骑手）。 */
    @PostMapping("/apply")
    public Result<LocalDelivery> apply(@CurrentUser LoginUser user,
                                       @Valid @RequestBody LocalDeliveryApplyRequest req) {
        return Result.ok(localDeliveryService.apply(user.getUserId(), req));
    }

    /** 取消配送单（未送达前）。 */
    @PostMapping("/cancel")
    public Result<Void> cancel(@CurrentUser LoginUser user, @RequestParam String deliveryNo) {
        localDeliveryService.cancel(user.getUserId(), deliveryNo);
        return Result.ok();
    }

    /** REQ-06 配送轨迹（回传 t_logistics 并归一化）。 */
    @GetMapping("/track")
    public Result<LocalDeliveryVO> track(@CurrentUser LoginUser user, @RequestParam String deliveryNo) {
        return Result.ok(localDeliveryService.track(deliveryNo));
    }

    /** 配送单详情（含已回传的本地轨迹时间轴）。 */
    @GetMapping("/detail")
    public Result<LocalDeliveryVO> detail(@CurrentUser LoginUser user, @RequestParam String deliveryNo) {
        return Result.ok(localDeliveryService.detail(user.getUserId(), deliveryNo));
    }

    /** 我的配送单（分页）。 */
    @GetMapping("/my")
    public Result<IPage<LocalDelivery>> my(@CurrentUser LoginUser user,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(localDeliveryService.myList(user.getUserId(), page, size));
    }
}
