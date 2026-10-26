package com.idlefish.trade.analytics.narrative;

import java.util.List;

/**
 * 经营健康度评分（ITER-U06）：纯函数，无 DB / 无 Spring 依赖，离线可单测。
 * <p>以退款率与 GMV 环比增长为输入，输出 0~100 综合分与分项明细；
 * 评分越高越健康，阈值：≥85 健康 / ≥60 关注 / &lt;60 预警。</p>
 */
public final class HealthScoreCalculator {

    private HealthScoreCalculator() {
    }

    /** 健康度分项（用于前端可视化条形）。 */
    public record HealthComponent(String name, int score, int max, String note) {
    }

    /** 健康度评分结果。 */
    public record HealthScore(int score, String level, List<HealthComponent> components) {
    }

    /**
     * 计算健康度。
     *
     * @param refundRate 当前周期退款率（0~1）
     * @param gmvGrowth  GMV 环比增长率（如 -0.2 表示下滑 20%）
     */
    public static HealthScore calc(double refundRate, double gmvGrowth) {
        int refundPenalty = (int) Math.min(40, refundRate * 1000); // 每 1% 退款率 -10，封顶 40
        int gmvPenalty = gmvGrowth < 0 ? (int) Math.min(30, -gmvGrowth * 100) : 0; // 每下滑 100% -100，封顶 30
        int score = clamp(100 - refundPenalty - gmvPenalty, 0, 100);

        String level = score >= 85 ? "健康" : (score >= 60 ? "关注" : "预警");
        List<HealthComponent> components = List.of(
                new HealthComponent("退款率", 100 - refundPenalty, 100, "退款率 " + MetricDictionary.pct(refundRate)),
                new HealthComponent("GMV 增长", gmvGrowth < 0 ? 100 - gmvPenalty : 100, 100,
                        "环比 " + MetricDictionary.pct(gmvGrowth))
        );
        return new HealthScore(score, level, components);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
