package com.idlefish.trade.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.marketing.activity.ActivityCore;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.marketing.activity.ActivityKind;
import com.idlefish.trade.marketing.activity.BargainConfig;
import com.idlefish.trade.marketing.activity.LotteryConfig;
import com.idlefish.trade.marketing.activity.LotteryPrize;
import com.idlefish.trade.marketing.activity.entity.ActivityReconciliation;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.marketing.activity.mapper.MarketingActivityMapper;
import com.idlefish.trade.marketing.activity.dto.MarketingActivityCreateDTO;
import com.idlefish.trade.marketing.activity.service.ActivityKernelService;
import com.idlefish.trade.marketing.activity.service.MktBargainService;
import com.idlefish.trade.marketing.activity.service.LiveSeckillService;
import com.idlefish.trade.marketing.activity.service.LotteryService;
import com.idlefish.trade.marketing.activity.vo.ActivityReconcileVO;
import com.idlefish.trade.marketing.activity.vo.MarketingActivityVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 营销活动运营后台接口（ITER-F08 / REQ-14、REQ-15 预算、REQ-13 人群、REQ-02 预热、REQ-11 对账）。
 * 向导式创建（含亏损阻断 REQ-14）、发布预热、结束对账、监控报表、抽奖奖品配置。
 */
@RestController
@RequestMapping("/api/admin/activity")
public class AdminActivityController {

    private final ActivityKernelService kernel;
    private final MktBargainService bargainService;
    private final LotteryService lotteryService;
    private final LiveSeckillService liveSeckillService;
    private final MarketingActivityMapper activityMapper;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metrics;

    public AdminActivityController(ActivityKernelService kernel, MktBargainService bargainService,
                                    LotteryService lotteryService, LiveSeckillService liveSeckillService,
                                    MarketingActivityMapper activityMapper, ObjectMapper objectMapper,
                                    MetricsRegistry metrics) {
        this.kernel = kernel;
        this.bargainService = bargainService;
        this.lotteryService = lotteryService;
        this.liveSeckillService = liveSeckillService;
        this.activityMapper = activityMapper;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    /** 向导式创建活动（含亏损阻断 REQ-14；草稿态，发布后开放）。 */
    @PostMapping("/create")
    public Result<Long> create(@CurrentUser LoginUser admin, @RequestBody MarketingActivityCreateDTO dto) {
        validateAndLossCheck(dto);
        MarketingActivity a = new MarketingActivity();
        a.setKind(dto.getKind());
        a.setTitle(dto.getTitle());
        a.setItemId(dto.getItemId());
        a.setActivityPrice(dto.getActivityPrice());
        a.setStock(dto.getStock());
        a.setLimitPerUser(dto.getLimitPerUser());
        a.setAudienceType(dto.getAudienceType());
        a.setAudienceValue(dto.getAudienceValue());
        a.setRiskLevel(dto.getRiskLevel());
        a.setSubsidyBudgetFen(dto.getSubsidyBudgetFen());
        a.setSubsidyUsedFen(0L);
        a.setLossFloorFen(dto.getLossFloorFen());
        a.setPlayConfig(dto.getPlayConfig());
        a.setStartAt(dto.getStartAt());
        a.setEndAt(dto.getEndAt());
        a.setStatus("DRAFT");
        activityMapper.insert(a);
        metrics.increment("activity.admin.create");
        return Result.ok(a.getId());
    }

    /** 发布活动（DRAFT → ONGOING + 库存预热，REQ-02）。 */
    @PostMapping("/{activityId}/publish")
    public Result<MarketingActivityVO> publish(@CurrentUser LoginUser admin, @PathVariable Long activityId) {
        return Result.ok(kernel.toVO(kernel.publish(activityId), null));
    }

    /** 结束并结算对账（REQ-11、REQ-15）。 */
    @PostMapping("/{activityId}/reconcile")
    public Result<ActivityReconcileVO> reconcile(@CurrentUser LoginUser admin, @PathVariable Long activityId) {
        kernel.reconcile(activityId);
        return Result.ok(kernel.toReconcileVO(activityId));
    }

    /** 活动列表（全部状态）。 */
    @GetMapping("/list")
    public Result<List<MarketingActivity>> list(@CurrentUser LoginUser admin) {
        return Result.ok(kernel.listAll());
    }

    /** 活动详情。 */
    @GetMapping("/{activityId}")
    public Result<MarketingActivityVO> detail(@CurrentUser LoginUser admin, @PathVariable Long activityId) {
        return Result.ok(kernel.getActivity(activityId, null));
    }

    /** 监控报表（参与 QPS/库存消耗/风控拦截/异常账户，REQ-15）。 */
    @GetMapping("/monitor")
    public Result<Map<String, Object>> monitor(@CurrentUser LoginUser admin) {
        Map<String, Object> m = new HashMap<>();
        int ongoing = countByStatus("ONGOING");
        int draft = countByStatus("DRAFT");
        int ended = countByStatus("ENDED");
        m.put("ongoing", ongoing);
        m.put("draft", draft);
        m.put("ended", ended);
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        m.put("joinedToday", kernel.myRecordsCountSince(todayStart));
        m.put("totalStock", totalStock());
        m.put("riskBlocked", metrics.counter("activity.risk.blocked"));
        m.put("subsidyBudgetExceeded", metrics.counter("activity.subsidy.budget_exceeded"));
        m.put("bargainQualified", metrics.counter("bargain.qualified"));
        m.put("lotteryWon", metrics.counter("lottery.draw.won"));
        return Result.ok(m);
    }

    /** 抽奖奖品配置（REQ-09 概率留痕 + REQ-14 亏损阻断）。 */
    @PostMapping("/lottery/prizes")
    public Result<Boolean> savePrizes(@CurrentUser LoginUser admin, @RequestParam Long activityId,
                                      @RequestBody List<LotteryPrize> prizes) {
        lotteryService.savePrizes(activityId, prizes);
        return Result.ok(true);
    }

    // ===== 辅助 =====

    private void validateAndLossCheck(MarketingActivityCreateDTO dto) {
        if (dto.getKind() == null || !isSupportedKind(dto.getKind())) {
            throw new BizException(Code.ACTIVITY_KIND_NOT_SUPPORTED);
        }
        if (dto.getStock() == null || dto.getStock() <= 0) {
            throw new BizException(Code.BIZ_ERROR, "活动库存必须大于 0");
        }
        long costFen = dto.getLossFloorFen() == null ? 0 : dto.getLossFloorFen();
        try {
            if (ActivityKind.BARGAIN.name().equals(dto.getKind())) {
                BargainConfig cfg = objectMapper.readValue(dto.getPlayConfig(), BargainConfig.class);
                if (cfg.getOriginPriceFen() == null || cfg.getFloorPriceFen() == null) {
                    throw new BizException(Code.BIZ_ERROR, "砍价原价与底价必填");
                }
                ActivityCore.assertLossSafe(cfg.getFloorPriceFen(), costFen);
            } else if (ActivityKind.LIVE_SECKILL.name().equals(dto.getKind())) {
                if (dto.getActivityPrice() == null) {
                    throw new BizException(Code.BIZ_ERROR, "秒杀价必填");
                }
                ActivityCore.assertLossSafe(dto.getActivityPrice(), costFen);
            }
            // LOTTERY 亏损拦截在奖品配置时校验（savePrizes）
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(Code.BIZ_ERROR, "玩法配置解析失败");
        }
    }

    private boolean isSupportedKind(String kind) {
        for (ActivityKind k : ActivityKind.values()) {
            if (k.name().equals(kind)) {
                return true;
            }
        }
        return false;
    }

    private int countByStatus(String status) {
        Long c = activityMapper.selectCount(new LambdaQueryWrapper<MarketingActivity>()
                .eq(MarketingActivity::getStatus, status));
        return c == null ? 0 : c.intValue();
    }

    private long totalStock() {
        List<MarketingActivity> list = activityMapper.selectList(new LambdaQueryWrapper<MarketingActivity>()
                .select(MarketingActivity::getStock));
        long sum = 0;
        for (MarketingActivity a : list) {
            sum += a.getStock() == null ? 0 : a.getStock();
        }
        return sum;
    }
}
