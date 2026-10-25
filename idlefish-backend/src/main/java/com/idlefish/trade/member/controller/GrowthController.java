package com.idlefish.trade.member.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.member.service.BenefitExchangeService;
import com.idlefish.trade.member.service.GrowthValueService;
import com.idlefish.trade.member.vo.BenefitExchangeVO;
import com.idlefish.trade.member.vo.BenefitVO;
import com.idlefish.trade.member.vo.GrowthAccountVO;
import com.idlefish.trade.member.vo.GrowthValueLogVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成长值用户端接口（ITER-F07 / REQ-11 用户端可见，需登录）：
 * 成长中心账户（等级权益 + 进度 + 衰减预警）、成长值流水、规则说明、权益兑换全链路。
 * <p>路径前缀 {@code /api/member/growth} 与 F-13.2 {@code /api/member/level} 并列，
 * 前者为 ITER-F07 深化后的唯一「成长中心」数据源。</p>
 */
@RestController
@RequestMapping("/api/member/growth")
public class GrowthController {

    private final GrowthValueService growthValueService;
    private final BenefitExchangeService benefitExchangeService;

    public GrowthController(GrowthValueService growthValueService,
                            BenefitExchangeService benefitExchangeService) {
        this.growthValueService = growthValueService;
        this.benefitExchangeService = benefitExchangeService;
    }

    /** 我的成长中心：等级权益 + 升级进度 + 冻结状态 + 距衰减天数。 */
    @GetMapping("/account")
    public Result<GrowthAccountVO> account(@CurrentUser LoginUser user) {
        return Result.ok(growthValueService.accountView(user.getUserId()));
    }

    /** 成长值流水（分页，可按来源过滤），支持对账。 */
    @GetMapping("/logs")
    public Result<IPage<GrowthValueLogVO>> logs(@CurrentUser LoginUser user,
                                                @RequestParam(required = false) String bizType,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(growthValueService.logs(user.getUserId(), bizType, page, size));
    }

    /** 成长值规则配置（获得口径 / 衰减规则 / 规则版本），供前端展示规则说明。 */
    @GetMapping("/config")
    public Result<IdlefishProperties.Growth> config() {
        return Result.ok(growthValueService.getConfig());
    }

    /** 权益兑换中心：上架中的权益 + 当前用户可兑换判定。 */
    @GetMapping("/benefits")
    public Result<IPage<BenefitVO>> benefits(@CurrentUser LoginUser user,
                                             @RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(benefitExchangeService.listAvailable(user.getUserId(), page, size));
    }

    /** 权益详情（含可兑换判定）。 */
    @GetMapping("/benefits/detail")
    public Result<BenefitVO> benefitDetail(@CurrentUser LoginUser user,
                                           @RequestParam Long benefitId) {
        return Result.ok(benefitExchangeService.detail(user.getUserId(), benefitId));
    }

    /** 权益兑换：等级 / 库存 / 积分 / 每日上限校验通过后原子发放，返回兑换单号。 */
    @PostMapping("/exchange")
    public Result<String> exchange(@CurrentUser LoginUser user,
                                   @RequestParam Long benefitId) {
        return Result.ok(benefitExchangeService.exchange(user.getUserId(), benefitId));
    }

    /** 我的兑换记录（分页，倒序）。 */
    @GetMapping("/exchanges")
    public Result<IPage<BenefitExchangeVO>> exchanges(@CurrentUser LoginUser user,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(benefitExchangeService.myExchanges(user.getUserId(), page, size));
    }

    /** 上报活跃（任意正向行为可调用）：刷新衰减基准，规避不活跃衰减。 */
    @PostMapping("/touch")
    public Result<Boolean> touch(@CurrentUser LoginUser user) {
        return Result.ok(growthValueService.touchActive(user.getUserId()));
    }
}
