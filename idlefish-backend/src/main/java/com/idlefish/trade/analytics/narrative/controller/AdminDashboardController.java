package com.idlefish.trade.analytics.narrative.controller;

import com.idlefish.trade.analytics.narrative.DashboardService;
import com.idlefish.trade.analytics.narrative.vo.DashboardOverviewVO;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.analytics.narrative.AnomalyDetector;
import com.idlefish.trade.analytics.narrative.ContributionAnalyzer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 经营数据可视化叙事接口（ITER-U06）：在 F-15.5 运营 BI 之上的「叙事层」。
 * <p>结论卡 / 健康度 / 异常波动 / 维度贡献度，统一经 {@link DashboardService} 聚合（Redis 缓存）。
 * 端点位于 /api/admin/dashboard，受后台鉴权拦截器保护。</p>
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    public AdminDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 看板概览：首屏结论卡 + 经营健康度 + 异常波动。 */
    @GetMapping("/overview")
    public Result<DashboardOverviewVO> overview(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(dashboardService.overview(days));
    }

    /** 异常波动（当前周期 vs 上一周期环比）。 */
    @GetMapping("/anomalies")
    public Result<List<AnomalyDetector.Anomaly>> anomalies(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(dashboardService.anomalies(days));
    }

    /** 维度贡献度（当前按品类 GMV 占比 + Top5；dimension 仅支持 category）。 */
    @GetMapping("/contribution")
    public Result<List<ContributionAnalyzer.Contribution>> contribution(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "category") String dimension) {
        return Result.ok(dashboardService.contribution(days, dimension));
    }
}
