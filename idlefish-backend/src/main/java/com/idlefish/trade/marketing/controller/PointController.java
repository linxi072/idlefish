package com.idlefish.trade.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.marketing.entity.Point;
import com.idlefish.trade.marketing.service.PointService;
import com.idlefish.trade.marketing.vo.PointLogVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分用户端接口（需登录）：余额 / 流水 / 签到 / 配置 / 抵现预览。
 */
@RestController
@RequestMapping("/api/point")
public class PointController {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    /** 我的积分余额与累计。 */
    @GetMapping("/balance")
    public Result<Point> balance(@CurrentUser LoginUser user) {
        return Result.ok(pointService.balance(user.getUserId()));
    }

    /** 积分流水（分页）。 */
    @GetMapping("/logs")
    public Result<IPage<PointLogVO>> logs(@CurrentUser LoginUser user,
                                          @RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(pointService.logs(user.getUserId(), page, size));
    }

    /** 每日签到得积分。 */
    @PostMapping("/signin")
    public Result<Integer> signin(@CurrentUser LoginUser user) {
        return Result.ok(pointService.signin(user.getUserId()));
    }

    /** 积分规则配置（兑换比例 / 使用上限等），供前端展示与试算。 */
    @GetMapping("/config")
    public Result<IdlefishProperties.Point> config() {
        return Result.ok(pointService.getConfig());
    }

    /** 积分抵现预览（试算抵扣金额，分），不落库。 */
    @GetMapping("/preview")
    public Result<Long> preview(@CurrentUser LoginUser user,
                               @RequestParam Long usedPoint,
                               @RequestParam Long payableFen) {
        return Result.ok(pointService.previewDiscount(user.getUserId(), usedPoint, payableFen));
    }
}
