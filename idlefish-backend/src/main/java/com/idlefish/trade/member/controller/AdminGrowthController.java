package com.idlefish.trade.member.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.member.entity.Benefit;
import com.idlefish.trade.member.service.BenefitExchangeService;
import com.idlefish.trade.member.service.GrowthDecayService;
import com.idlefish.trade.member.service.GrowthDecayService.DecaySummary;
import com.idlefish.trade.member.service.GrowthValueService;
import com.idlefish.trade.member.vo.BenefitExchangeVO;
import com.idlefish.trade.member.vo.GrowthAccountAdminVO;
import com.idlefish.trade.member.vo.GrowthAccountVO;
import com.idlefish.trade.member.vo.GrowthValueLogVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成长值运营后台（ITER-F07 / PRD §六）：规则配置、账户与流水对账、刷分名单冻结解冻、
 * 权益目录上下架、兑换记录、衰减任务手动触发。
 * <p>路径前缀 {@code /api/admin/growth} 与 F-13.2 {@code /api/admin/member/level} 分工：
 * 后者管等级阈值配置，前者管成长值运营（账户 / 流水 / 冻结 / 权益 / 衰减）。</p>
 */
@RestController
@RequestMapping("/api/admin/growth")
public class AdminGrowthController {

    private final GrowthValueService growthValueService;
    private final BenefitExchangeService benefitExchangeService;
    private final GrowthDecayService growthDecayService;

    public AdminGrowthController(GrowthValueService growthValueService,
                                 BenefitExchangeService benefitExchangeService,
                                 GrowthDecayService growthDecayService) {
        this.growthValueService = growthValueService;
        this.benefitExchangeService = benefitExchangeService;
        this.growthDecayService = growthDecayService;
    }

    /** 成长值规则配置（获得口径 / 防刷阈值 / 衰减参数 / 规则版本）。 */
    @GetMapping("/config")
    public Result<IdlefishProperties.Growth> config(@CurrentAdmin AdminUser admin) {
        return Result.ok(growthValueService.getConfig());
    }

    /** 成长值账户列表（分页，可按冻结状态过滤）——刷分名单与流失预警。 */
    @GetMapping("/accounts")
    public Result<IPage<GrowthAccountAdminVO>> accounts(@CurrentAdmin AdminUser admin,
                                                        @RequestParam(required = false) Boolean frozenOnly,
                                                        @RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(growthValueService.listAccounts(page, size, frozenOnly));
    }

    /** 单个用户成长值账户（只读，不惰性创建）。 */
    @GetMapping("/account")
    public Result<GrowthAccountVO> account(@CurrentAdmin AdminUser admin,
                                           @RequestParam Long userId) {
        return Result.ok(growthValueService.accountViewReadonly(userId));
    }

    /** 指定用户成长值流水（可按来源过滤）——REQ-09 对账。 */
    @GetMapping("/logs")
    public Result<IPage<GrowthValueLogVO>> logs(@CurrentAdmin AdminUser admin,
                                                @RequestParam Long userId,
                                                @RequestParam(required = false) String bizType,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(growthValueService.logs(userId, bizType, page, size));
    }

    /** 刷分冻结名单（frozenOnly=true 的账户分页）。 */
    @GetMapping("/antifraud")
    public Result<IPage<GrowthAccountAdminVO>> antifraud(@CurrentAdmin AdminUser admin,
                                                         @RequestParam(defaultValue = "1") Integer page,
                                                         @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(growthValueService.listAccounts(page, size, true));
    }

    /** 冻结发放通道（REQ-03）：命中刷分或账号封禁时手动冻结。 */
    @PostMapping("/freeze")
    public Result<Boolean> freeze(@CurrentAdmin AdminUser admin,
                                  @RequestParam Long userId,
                                  @RequestParam String reason) {
        return Result.ok(growthValueService.freeze(userId, reason));
    }

    /** 解冻发放通道；已跳过的成长值不回补。 */
    @PostMapping("/unfreeze")
    public Result<Boolean> unfreeze(@CurrentAdmin AdminUser admin,
                                    @RequestParam Long userId) {
        return Result.ok(growthValueService.unfreeze(userId));
    }

    /** 权益配置清单（全部，按排序）。 */
    @GetMapping("/benefits")
    public Result<List<Benefit>> benefits(@CurrentAdmin AdminUser admin) {
        return Result.ok(benefitExchangeService.adminList());
    }

    /** 新增 / 更新权益配置（按 id 是否存在 upsert）。 */
    @PostMapping("/benefits/save")
    public Result<Long> saveBenefit(@CurrentAdmin AdminUser admin,
                                    @RequestBody Benefit benefit) {
        return Result.ok(benefitExchangeService.save(benefit));
    }

    /** 删除权益配置。 */
    @PostMapping("/benefits/delete")
    public Result<Void> deleteBenefit(@CurrentAdmin AdminUser admin,
                                      @RequestParam Long id) {
        benefitExchangeService.delete(id);
        return Result.ok();
    }

    /** 权益上下架（REQ-12）。 */
    @PostMapping("/benefits/status")
    public Result<Boolean> updateBenefitStatus(@CurrentAdmin AdminUser admin,
                                               @RequestParam Long id,
                                               @RequestParam String status) {
        return Result.ok(benefitExchangeService.updateStatus(id, status));
    }

    /** 兑换记录（分页，可按用户过滤）——积分消耗对账。 */
    @GetMapping("/exchanges")
    public Result<IPage<BenefitExchangeVO>> exchanges(@CurrentAdmin AdminUser admin,
                                                      @RequestParam(required = false) Long userId,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(benefitExchangeService.adminExchanges(userId, page, size));
    }

    /**
     * 手动触发衰减任务（补跑 / 演练）：
     * {@code notify=false}（默认）执行衰减扫描，{@code notify=true} 执行提前预警。
     */
    @PostMapping("/decay/run")
    public Result<DecaySummary> runDecay(@CurrentAdmin AdminUser admin,
                                         @RequestParam(defaultValue = "false") boolean notify) {
        DecaySummary s = notify ? growthDecayService.notifyUpcomingDecay()
                : growthDecayService.scanAndDecay();
        return Result.ok(s);
    }

    /**
     * 存量用户成长值账户初始化（PRD §12.4 遗留项②）：为尚未建立账户的存量用户批量补建
     * 默认 L1 / 0 账户。{@code limit} 控制单次批量规模，返回本次初始化数量；可分页多次调用。
     */
    @PostMapping("/init-stock")
    public Result<Integer> initStock(@CurrentAdmin AdminUser admin,
                                     @RequestParam(defaultValue = "1000") int limit) {
        return Result.ok(growthValueService.initMissingUsers(limit));
    }

    /**
     * 申诉成功回补判责扣减（REQ-10）：按工单号自动反查历史判责扣减量并回补，幂等。
     * 供运营在维权申诉裁定通过后手动触发；无判责扣减或已回补时返回 0。
     */
    @PostMapping("/restore-by-dispute")
    public Result<Integer> restoreByDispute(@CurrentAdmin AdminUser admin,
                                            @RequestParam Long userId,
                                            @RequestParam String disputeNo) {
        return Result.ok(growthValueService.restoreByDispute(userId, disputeNo));
    }
}
