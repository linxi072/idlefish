package com.idlefish.trade.analytics.service;

import com.idlefish.trade.analytics.AnalyticsCalculator;
import com.idlefish.trade.analytics.mapper.AnalyticsMapper;
import com.idlefish.trade.analytics.vo.AnalyticsCategoryGmv;
import com.idlefish.trade.analytics.vo.AnalyticsCondition;
import com.idlefish.trade.analytics.vo.AnalyticsFunnelStage;
import com.idlefish.trade.analytics.vo.AnalyticsOverview;
import com.idlefish.trade.analytics.vo.CsvExport;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营 BI 聚合服务（F-15.5）。
 * <p>只读聚合，不直接写库；所有派生指标经 {@link AnalyticsCalculator} 纯函数计算，便于单测。</p>
 */
@Service
public class AdminAnalyticsService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;

    private final AnalyticsMapper analyticsMapper;

    public AdminAnalyticsService(AnalyticsMapper analyticsMapper) {
        this.analyticsMapper = analyticsMapper;
    }

    /** 概览：GMV / 订单 / 退款率 / 客单价 / 用户 / 商品 + 每日趋势。 */
    public AnalyticsOverview overview(int days) {
        LocalDateTime start = LocalDateTime.now().minusDays(clampDays(days));
        Map<String, Object> totals = analyticsMapper.overviewTotals(start);
        List<Map<String, Object>> trend = analyticsMapper.dailyTrend(start);

        long gmv = toLong(totals.get("gmv"));
        long paid = toLong(totals.get("paidOrderCount"));
        long refund = toLong(totals.get("refundOrderCount"));
        long total = toLong(totals.get("totalOrderCount"));

        AnalyticsOverview o = new AnalyticsOverview();
        o.setGmv(gmv);
        o.setPaidOrderCount(paid);
        o.setTotalOrderCount(total);
        o.setRefundOrderCount(refund);
        o.setRefundRate(AnalyticsCalculator.refundRate(refund, paid));
        o.setAvgOrderValue(AnalyticsCalculator.avgOrderValue(gmv, paid));
        o.setUserCount(analyticsMapper.userCount());
        o.setItemOnsaleCount(analyticsMapper.itemOnsaleCount());
        o.setItemTotalCount(analyticsMapper.itemTotalCount());
        o.setTrend(trend);
        return o;
    }

    /** 转化漏斗：浏览 → 搜索 → 收藏 → 下单 → 支付（含环比/整体转化率）。 */
    public List<AnalyticsFunnelStage> funnel(int days) {
        LocalDateTime start = LocalDateTime.now().minusDays(clampDays(days));
        List<Map<String, Object>> rows = analyticsMapper.funnelCounts(start);
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> r : rows) {
            counts.put((String) r.get("event"), toLong(r.get("cnt")));
        }
        return AnalyticsCalculator.buildFunnel(counts);
    }

    /** 品类 GMV 分布。 */
    public List<AnalyticsCategoryGmv> category(int days) {
        LocalDateTime start = LocalDateTime.now().minusDays(clampDays(days));
        List<Map<String, Object>> rows = analyticsMapper.categoryGmv(start);
        List<AnalyticsCategoryGmv> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            AnalyticsCategoryGmv c = new AnalyticsCategoryGmv();
            c.setCategoryId(toLong(r.get("categoryId")));
            c.setCategoryName((String) r.get("categoryName"));
            c.setOrderCount(toLong(r.get("orderCount")));
            c.setGmv(toLong(r.get("gmv")));
            out.add(c);
        }
        return out;
    }

    /** 商品成色分布。 */
    public List<AnalyticsCondition> condition() {
        List<Map<String, Object>> rows = analyticsMapper.conditionDist();
        List<AnalyticsCondition> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            AnalyticsCondition c = new AnalyticsCondition();
            c.setConditionLevel(toInt(r.get("conditionLevel")));
            c.setCount(toLong(r.get("cnt")));
            out.add(c);
        }
        return out;
    }

    /** CSV 导出：summary（概览 KPI）/ gmv（每日趋势）/ funnel / category / condition。 */
    public CsvExport export(String type, int days) {
        switch (type) {
            case "summary":
                return exportSummary(days);
            case "gmv":
                return exportTrend(days);
            case "funnel":
                return exportFunnel(days);
            case "category":
                return exportCategory(days);
            case "condition":
                return exportCondition();
            default:
                throw new BizException(Code.PARAM_INVALID, "unsupported export type: " + type);
        }
    }

    // ===== 导出实现 =====

    private CsvExport exportSummary(int days) {
        AnalyticsOverview o = overview(days);
        List<String> headers = List.of("指标", "数值");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("GMV(分)", str(o.getGmv())));
        rows.add(List.of("GMV(元)", str(fenToYuan(o.getGmv()))));
        rows.add(List.of("支付订单数", str(o.getPaidOrderCount())));
        rows.add(List.of("订单总数", str(o.getTotalOrderCount())));
        rows.add(List.of("退款订单数", str(o.getRefundOrderCount())));
        rows.add(List.of("退款率", String.format("%.4f", o.getRefundRate())));
        rows.add(List.of("客单价(分)", str(o.getAvgOrderValue())));
        rows.add(List.of("客单价(元)", str(fenToYuan(o.getAvgOrderValue()))));
        rows.add(List.of("注册用户数", str(o.getUserCount())));
        rows.add(List.of("在售商品数", str(o.getItemOnsaleCount())));
        rows.add(List.of("商品总数", str(o.getItemTotalCount())));
        return new CsvExport("analytics-summary-" + dateStamp() + ".csv", buildCsv(headers, rows));
    }

    private CsvExport exportTrend(int days) {
        AnalyticsOverview o = overview(days);
        List<String> headers = List.of("日期", "GMV(分)", "GMV(元)", "支付订单数", "订单总数");
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> t : o.getTrend()) {
            long gmv = toLong(t.get("gmv"));
            rows.add(List.of(str(t.get("day")), str(gmv), str(fenToYuan(gmv)),
                    str(t.get("paidCount")), str(t.get("orderCount"))));
        }
        return new CsvExport("analytics-gmv-" + dateStamp() + ".csv", buildCsv(headers, rows));
    }

    private CsvExport exportFunnel(int days) {
        List<AnalyticsFunnelStage> funnel = funnel(days);
        List<String> headers = List.of("阶段", "事件数", "环比转化率", "整体转化率");
        List<List<String>> rows = new ArrayList<>();
        for (AnalyticsFunnelStage s : funnel) {
            rows.add(List.of(s.getStage(), str(s.getCount()),
                    String.format("%.4f", s.getStepRate()), String.format("%.4f", s.getOverallRate())));
        }
        return new CsvExport("analytics-funnel-" + dateStamp() + ".csv", buildCsv(headers, rows));
    }

    private CsvExport exportCategory(int days) {
        List<AnalyticsCategoryGmv> list = category(days);
        List<String> headers = List.of("类目ID", "类目名称", "订单数", "GMV(分)", "GMV(元)");
        List<List<String>> rows = new ArrayList<>();
        for (AnalyticsCategoryGmv c : list) {
            rows.add(List.of(str(c.getCategoryId()), c.getCategoryName(), str(c.getOrderCount()),
                    str(c.getGmv()), str(fenToYuan(c.getGmv()))));
        }
        return new CsvExport("analytics-category-" + dateStamp() + ".csv", buildCsv(headers, rows));
    }

    private CsvExport exportCondition() {
        List<AnalyticsCondition> list = condition();
        List<String> headers = List.of("成色等级", "成色描述", "商品数");
        List<List<String>> rows = new ArrayList<>();
        for (AnalyticsCondition c : list) {
            rows.add(List.of(str(c.getConditionLevel()), conditionLabel(c.getConditionLevel()), str(c.getCount())));
        }
        return new CsvExport("analytics-condition-" + dateStamp() + ".csv", buildCsv(headers, rows));
    }

    // ===== 辅助 =====

    private static String conditionLabel(Integer level) {
        if (level == null) {
            return "未知";
        }
        return switch (level) {
            case 1 -> "全新";
            case 2 -> "95新";
            case 3 -> "9成新";
            case 4 -> "8成新";
            case 5 -> "功能完好";
            default -> "其他";
        };
    }

    private int clampDays(int days) {
        if (days < MIN_DAYS) {
            return MIN_DAYS;
        }
        if (days > MAX_DAYS) {
            return MAX_DAYS;
        }
        return days;
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private static int toInt(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static long fenToYuan(long fen) {
        return fen / 100;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String dateStamp() {
        return LocalDateTime.now().toLocalDate().toString();
    }

    /** 按 RFC 4180 简单转义构建 CSV 文本。 */
    private static String buildCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(joinCsv(headers)).append("\n");
        for (List<String> r : rows) {
            sb.append(joinCsv(r)).append("\n");
        }
        return sb.toString();
    }

    private static String joinCsv(List<String> cells) {
        List<String> escaped = new ArrayList<>();
        for (String c : cells) {
            String v = c == null ? "" : c;
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                escaped.add("\"" + v.replace("\"", "\"\"") + "\"");
            } else {
                escaped.add(v);
            }
        }
        return String.join(",", escaped);
    }
}
