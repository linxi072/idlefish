package com.idlefish.trade.analytics;

import com.idlefish.trade.analytics.vo.AnalyticsFunnelStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 运营 BI 派生指标计算（F-15.5）——纯函数，无 DB / 无 Spring 依赖，离线可单测。
 * <p>所有比率返回 0.0~1.0；分母为 0 时统一返回 0.0（避免 NaN / 除零异常）。</p>
 */
public final class AnalyticsCalculator {

    private AnalyticsCalculator() {
    }

    /** 退款率 = 退款订单数 / 支付订单数。 */
    public static double refundRate(long refundCount, long paidCount) {
        if (paidCount <= 0) {
            return 0.0;
        }
        return (double) refundCount / paidCount;
    }

    /** 客单价（分）= GMV / 支付订单数。 */
    public static long avgOrderValue(long gmvFen, long paidCount) {
        if (paidCount <= 0) {
            return 0L;
        }
        return gmvFen / paidCount;
    }

    /** 环比转化率 = 本级 / 上一级。 */
    public static double stepRate(long prev, long cur) {
        if (prev <= 0) {
            return 0.0;
        }
        return (double) cur / prev;
    }

    /** 整体转化率 = 本级 / 漏斗首级。 */
    public static double overallRate(long top, long cur) {
        if (top <= 0) {
            return 0.0;
        }
        return (double) cur / top;
    }

    private static final String[] FUNNEL_EVENTS = {"view_item", "search", "favorite", "order_create", "pay"};
    private static final String[] FUNNEL_LABELS = {"商品浏览", "搜索", "收藏", "下单", "支付"};

    /**
     * 由事件计数组装有序漏斗。缺失事件按 0 计；首级整体/环比转化率记为 1.0。
     */
    public static List<AnalyticsFunnelStage> buildFunnel(Map<String, Long> eventCounts) {
        List<AnalyticsFunnelStage> out = new ArrayList<>();
        Long prev = null;
        Long top = null;
        for (int i = 0; i < FUNNEL_EVENTS.length; i++) {
            long cnt = eventCounts.getOrDefault(FUNNEL_EVENTS[i], 0L);
            double step = (prev == null) ? 1.0 : stepRate(prev, cnt);
            double overall = (top == null) ? 1.0 : overallRate(top, cnt);
            out.add(new AnalyticsFunnelStage(FUNNEL_LABELS[i], cnt, step, overall));
            if (prev == null) {
                top = cnt;
            }
            prev = cnt;
        }
        return out;
    }
}
