package com.idlefish.trade.pricing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 估价纯函数（ITER-F03）：无状态、无浮点资损、可离线单测（REQ-01/REQ-07）。
 *
 * <p>核心能力：
 * <ul>
 *   <li>成色折损系数（与 U01 规则保持一致）；</li>
 *   <li>IQR 样本清洗（剔除离群，防自成交刷价，边界5）；</li>
 *   <li>分位估价区间（p25/中位/p75）；</li>
 *   <li>规则兜底（基准价 × 成色系数 ±10%，样本不足时回落，REQ-03）；</li>
 *   <li>运营系数覆盖（对区间整体乘系数，REQ-08）；</li>
 *   <li>偏离判定（REQ-09，提示不阻断）与异常低价判定（REQ-10，送审）。</li>
 * </ul>
 *
 * <p>所有金额恒为「分」整型；区间返回 {min, median, max} 三元组，无数据返回 null。
 */
public final class PriceEstimator {

    /** 最小有效样本数（低于则回落规则兜底，REQ-03）。 */
    public static final int MIN_SAMPLE_SIZE = 5;
    /** 样本清洗 IQR 倍数（离群判定：超出 [Q1-1.5IQR, Q3+1.5IQR]）。 */
    public static final double IQR_FACTOR = 1.5;
    /** 偏离提示：低于区间下限 50%（REQ-09）。 */
    public static final double DEVIATION_LOW_RATIO = 0.5;
    /** 偏离提示：高于区间上限 100%（即 > 2×max，REQ-09）。 */
    public static final double DEVIATION_HIGH_RATIO = 1.0;

    public static final String DEVIATION_NONE = "NONE";
    public static final String DEVIATION_LOW = "LOW";
    public static final String DEVIATION_HIGH = "HIGH";

    public static final String SOURCE_SAMPLE = "sample";
    public static final String SOURCE_RULE = "rule";

    private PriceEstimator() {
    }

    /** 默认成色折损系数（与 publish 规则保持一致：1 全新 ~ 5 功能完好）。 */
    public static double conditionCoefficient(int level) {
        return switch (level) {
            case 1 -> 1.0;
            case 2 -> 0.85;
            case 3 -> 0.70;
            case 4 -> 0.55;
            case 5 -> 0.40;
            default -> 0.5;
        };
    }

    /**
     * IQR 样本清洗：剔除离群值，返回升序清洗后样本（不修改入参）。
     * 样本数 < 4 时无法稳健估计 IQR，原样返回（仅去 null/非正）。
     */
    public static List<Long> cleanSamples(List<Long> raw) {
        List<Long> sorted = new ArrayList<>();
        if (raw != null) {
            for (Long v : raw) {
                if (v != null && v > 0) {
                    sorted.add(v);
                }
            }
        }
        Collections.sort(sorted);
        int n = sorted.size();
        if (n < 4) {
            return sorted;
        }
        double q1 = percentile(sorted, 0.25);
        double q3 = percentile(sorted, 0.75);
        double iqr = q3 - q1;
        long low = (long) Math.floor(q1 - IQR_FACTOR * iqr);
        long high = (long) Math.ceil(q3 + IQR_FACTOR * iqr);
        List<Long> cleaned = new ArrayList<>();
        for (Long v : sorted) {
            if (v >= low && v <= high) {
                cleaned.add(v);
            }
        }
        return cleaned;
    }

    /** 线性插值百分位（sorted 升序，p ∈ [0,1]）。空/单元素安全返回。 */
    public static long percentile(List<Long> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) {
            return 0L;
        }
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double rank = p * (sorted.size() - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sorted.get(lo);
        }
        double frac = rank - lo;
        long a = sorted.get(lo);
        long b = sorted.get(hi);
        return Math.round(a + frac * (b - a));
    }

    /**
     * 基于同成色样本的分位估价区间：{min=p25, median=p50, max=p75}。
     * 清洗后样本不足（< MIN_SAMPLE_SIZE）返回 null（交由调用方回落规则兜底，REQ-03）。
     */
    public static long[] sampleRange(List<Long> rawSamples) {
        List<Long> cleaned = cleanSamples(rawSamples);
        if (cleaned.size() < MIN_SAMPLE_SIZE) {
            return null;
        }
        long min = percentile(cleaned, 0.25);
        long median = percentile(cleaned, 0.50);
        long max = percentile(cleaned, 0.75);
        return new long[]{min, median, max};
    }

    /**
     * 规则兜底区间（分）：baseFen × 成色系数，上下浮动 10%。
     * baseFen ≤ 0 返回 null（无基准价，REQ-05 降级）。
     */
    public static long[] ruleFallback(long baseFen, int level) {
        if (baseFen <= 0) {
            return null;
        }
        double coef = conditionCoefficient(level);
        long center = Math.round(baseFen * coef);
        long min = Math.round(center * 0.9);
        long max = Math.round(center * 1.1);
        return new long[]{min, center, max};
    }

    /** 运营系数覆盖：对区间整体乘以系数（multiplier≈1.0 时无变化，REQ-08）。 */
    public static long[] applyOverride(long[] range, double multiplier) {
        if (range == null || multiplier <= 0) {
            return range;
        }
        return new long[]{
                Math.round(range[0] * multiplier),
                Math.round(range[1] * multiplier),
                Math.round(range[2] * multiplier)
        };
    }

    /**
     * REQ-09 偏离判定：price 相对 [min,max] 的偏离。
     * 低于下限 50% → LOW；高于上限 100%（>2×max）→ HIGH；否则 NONE。仅提示，不阻断。
     */
    public static String checkDeviation(long price, long min, long max) {
        if (min > 0 && price < Math.round(min * DEVIATION_LOW_RATIO)) {
            return DEVIATION_LOW;
        }
        if (max > 0 && price > Math.round(max * (1 + DEVIATION_HIGH_RATIO))) {
            return DEVIATION_HIGH;
        }
        return DEVIATION_NONE;
    }

    /** REQ-10 异常低价判定：低于区间下限 50% 视为异常低价（需送审）。 */
    public static boolean isAbnormalLow(long price, long min) {
        return min > 0 && price > 0 && price < Math.round(min * DEVIATION_LOW_RATIO);
    }
}
