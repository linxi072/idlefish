package com.idlefish.trade.analytics.narrative;

import java.util.ArrayList;
import java.util.List;

/**
 * 异常检测（ITER-U06）：纯函数，无 DB / 无 Spring 依赖，离线可单测。
 * <p>以「当前周期 vs 上一周期」同口径对比，对退款率、GMV、支付订单数、客单价等核心指标的
 * 异常波动给出 severity 分级与可读信息，供看板异常卡与预警订阅消费。</p>
 */
public final class AnomalyDetector {

    private AnomalyDetector() {
    }

    /** 单条异常。 */
    public record Anomaly(String metric, double current, double previous, double delta,
                          String severity, String message) {
    }

    /**
     * 检测异常波动。
     *
     * @param refundRateCur  当前退款率
     * @param refundRatePrev 上期退款率
     * @param gmvGrowth      GMV 环比增长率
     * @param paidGrowth     支付订单数环比增长率
     * @param aovGrowth      客单价环比增长率
     */
    public static List<Anomaly> detect(double refundRateCur, double refundRatePrev,
                                       double gmvGrowth, double paidGrowth, double aovGrowth) {
        List<Anomaly> out = new ArrayList<>();

        // 退款率环比上升超过 2 个百分点 -> warn
        double refundDelta = refundRateCur - refundRatePrev;
        if (refundDelta > 0.02) {
            out.add(new Anomaly("refundRate", refundRateCur, refundRatePrev, refundDelta, "warn",
                    "退款率环比上升 " + MetricDictionary.pct(refundDelta) + "，关注售后体验与纠纷处理"));
        }

        // GMV 环比下滑超 20% -> bad
        if (gmvGrowth < -0.2) {
            out.add(new Anomaly("gmv", 0, 0, gmvGrowth, "bad",
                    "GMV 环比下滑 " + MetricDictionary.pct(-gmvGrowth) + "，低于 -20% 阈值"));
        }

        // 支付订单数环比下滑超 20% -> bad
        if (paidGrowth < -0.2) {
            out.add(new Anomaly("paidOrderCount", 0, 0, paidGrowth, "bad",
                    "支付订单数环比下滑 " + MetricDictionary.pct(-paidGrowth)));
        }

        // 客单价环比下滑超 20% -> warn
        if (aovGrowth < -0.2) {
            out.add(new Anomaly("avgOrderValue", 0, 0, aovGrowth, "warn",
                    "客单价环比下滑 " + MetricDictionary.pct(-aovGrowth) + "，关注促销透支或品类结构变化"));
        }

        return out;
    }
}
