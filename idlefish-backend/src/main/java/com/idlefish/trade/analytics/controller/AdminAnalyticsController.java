package com.idlefish.trade.analytics.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.analytics.service.AdminAnalyticsService;
import com.idlefish.trade.analytics.vo.AnalyticsCategoryGmv;
import com.idlefish.trade.analytics.vo.AnalyticsCondition;
import com.idlefish.trade.analytics.vo.AnalyticsFunnelStage;
import com.idlefish.trade.analytics.vo.AnalyticsOverview;
import com.idlefish.trade.analytics.vo.CsvExport;
import com.idlefish.trade.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 运营 BI 报表接口（F-15.5）。
 * 概览 / 转化漏斗 / 品类 GMV 分布 / 成色分布 / CSV 导出；所有读接口经 RBAC 鉴权。
 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AdminAnalyticsService analyticsService;

    public AdminAnalyticsController(AdminAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** 运营概览（默认近 30 天）。 */
    @GetMapping("/overview")
    public Result<AnalyticsOverview> overview(@CurrentAdmin AdminUser admin,
                                              @RequestParam(defaultValue = "30") int days) {
        return Result.ok(analyticsService.overview(days));
    }

    /** 转化漏斗。 */
    @GetMapping("/funnel")
    public Result<List<AnalyticsFunnelStage>> funnel(@CurrentAdmin AdminUser admin,
                                                     @RequestParam(defaultValue = "30") int days) {
        return Result.ok(analyticsService.funnel(days));
    }

    /** 品类 GMV 分布。 */
    @GetMapping("/category")
    public Result<List<AnalyticsCategoryGmv>> category(@CurrentAdmin AdminUser admin,
                                                       @RequestParam(defaultValue = "30") int days) {
        return Result.ok(analyticsService.category(days));
    }

    /** 商品成色分布。 */
    @GetMapping("/condition")
    public Result<List<AnalyticsCondition>> condition(@CurrentAdmin AdminUser admin) {
        return Result.ok(analyticsService.condition());
    }

    /** CSV 导出（type = summary | gmv | funnel | category | condition）。 */
    @GetMapping(value = "/export", produces = "text/csv;charset=utf-8")
    public void export(@CurrentAdmin AdminUser admin,
                       @RequestParam String type,
                       @RequestParam(defaultValue = "30") int days,
                       HttpServletResponse response) throws IOException {
        CsvExport ex = analyticsService.export(type, days);
        response.setContentType("text/csv;charset=utf-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + ex.getFilename() + "\"");
        // 写入 UTF-8 BOM，保证 Excel 正确识别中文
        response.getWriter().write("﻿" + ex.getContent());
    }
}
