package com.idlefish.trade.analytics.narrative;

import java.util.Map;

/**
 * 指标字典（ITER-U06）：经营看板所有指标的唯一口径定义，杜绝多端/多报表口径不一致。
 * <p>纯静态、无 DB / 无 Spring 依赖；提供语义判定（good/warn/bad）与结论文案，供看板结论卡复用。</p>
 */
public final class MetricDictionary {

    private MetricDictionary() {
    }

    /** 指标方向：越高越好 / 越低越好 / 中性。 */
    public enum Direction {
        HIGHER_BETTER, LOWER_BETTER, NEUTRAL
    }

    /** 指标定义：阈值用于语义判定（good/warn/bad）。 */
    public record MetricDef(String key, String label, String unit, Direction direction,
                            double goodThreshold, double warnThreshold) {
    }

    public static final Map<String, MetricDef> METRICS = Map.of(
            "gmv", new MetricDef("gmv", "GMV", "元", Direction.HIGHER_BETTER, 0, 0),
            "paidOrderCount", new MetricDef("paidOrderCount", "支付订单数", "笔", Direction.HIGHER_BETTER, 0, 0),
            "refundRate", new MetricDef("refundRate", "退款率", "", Direction.LOWER_BETTER, 0.03, 0.08),
            "avgOrderValue", new MetricDef("avgOrderValue", "客单价", "元", Direction.HIGHER_BETTER, 0, 0),
            "userCount", new MetricDef("userCount", "注册用户数", "人", Direction.HIGHER_BETTER, 0, 0),
            "itemOnsaleCount", new MetricDef("itemOnsaleCount", "在售商品数", "件", Direction.HIGHER_BETTER, 0, 0)
    );

    public static MetricDef def(String key) {
        return METRICS.get(key);
    }

    /** 业务语义判定：依据方向与阈值给出 good / warn / bad / neutral。 */
    public static String judge(String key, double value) {
        MetricDef d = def(key);
        if (d == null) {
            return "neutral";
        }
        return switch (d.direction()) {
            case HIGHER_BETTER -> value >= d.goodThreshold() ? "good" : "neutral";
            case LOWER_BETTER -> value <= d.goodThreshold() ? "good"
                    : (value <= d.warnThreshold() ? "warn" : "bad");
            default -> "neutral";
        };
    }

    /** 结论文案：依据语义等级生成可读结论。 */
    public static String conclusion(String key, double value, String unit) {
        MetricDef d = def(key);
        if (d == null) {
            return "";
        }
        String fmt = format(value, unit);
        return switch (judge(key, value)) {
            case "good" -> d.label() + "表现良好：" + fmt;
            case "warn" -> d.label() + "需关注：" + fmt;
            case "bad" -> d.label() + "预警：" + fmt;
            default -> d.label() + "：" + fmt;
        };
    }

    /** 金额/比率格式化：元保留整数，比率转百分比，其余原值。 */
    public static String format(double value, String unit) {
        if ("元".equals(unit)) {
            return Math.round(value) + "元";
        }
        if ("".equals(unit)) {
            return String.format("%.2f%%", value * 100);
        }
        return String.format("%.2f%s", value, unit);
    }

    /** 比率转百分比字符串（如 0.0321 -> 3.21%）。 */
    public static String pct(double ratio) {
        return String.format("%.2f%%", ratio * 100);
    }
}
