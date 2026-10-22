package com.idlefish.trade.local;

/**
 * 回收估价纯函数（ITER-F05 / REQ-02、REQ-04）：无状态、无 IO，可离线单测。
 *
 * <p>估价链路（金额恒「分」，全程先乘后除避免浮点截断误差）：
 * <pre>
 *   预估价 = clamp(参考价 × 成色系数, minEstimateFen, 参考价)
 *   最终价 = 服务端封顶后的质检价（<= 预估价上限保护：不得超过预估价的 (1 + upBound)）
 *   跌幅   = (预估价 - 最终价) / 预估价，超过 maxQuoteDropRatio 判定为异常调价，需人工复核
 * </pre>
 *
 * <p>资金安全纪律（沿用 DisputeService 的封顶范式）：任何外部传入金额均需服务端二次封顶，
 * 绝不信任客户端报价。
 */
public final class RecycleQuoteCalculator {

    /** 预估价相对参考价的上限保护（1.0 = 预估价不得超过参考价）。 */
    public static final double ESTIMATE_UPPER_RATIO = 1.0;
    /** 最终价相对预估价的允许上浮上限（0.2 = 最多上浮 20%，褒奖实物优于描述）。 */
    public static final double FINAL_UP_BOUND_RATIO = 0.2;

    private RecycleQuoteCalculator() {
    }

    /**
     * 依据参考价（分）与成色档位计算预估回收价（分）。
     * 结果为 {@code long}，按向下取整；并保证不低于 {@code minFen}、不高于参考价。
     *
     * @param referenceFen  参考价（分，来自 ITER-F03 估价引擎或运营配置基准价）
     * @param conditionLevel 成色档位 1~5
     * @param ratioByLevel  成色 → 系数映射（缺省时用 defaultRatio）
     * @param defaultRatio  无匹配档位时的兜底系数
     * @param minFen        预估价下限（分），防止 0 元单与无意义上门
     * @return 预估回收价（分）
     */
    public static long estimate(long referenceFen, Integer conditionLevel,
                                java.util.Map<Integer, Double> ratioByLevel,
                                double defaultRatio, long minFen) {
        if (referenceFen <= 0) {
            return 0L;
        }
        double ratio = defaultRatio;
        if (conditionLevel != null && ratioByLevel != null) {
            Double r = ratioByLevel.get(conditionLevel);
            if (r != null && r > 0) {
                ratio = r;
            }
        }
        if (ratio <= 0) {
            ratio = defaultRatio;
        }
        long amount = (long) (referenceFen * ratio);
        long cap = (long) (referenceFen * ESTIMATE_UPPER_RATIO);
        if (amount > cap) {
            amount = cap;
        }
        if (minFen > 0 && amount < minFen) {
            amount = minFen;
        }
        return amount;
    }

    /**
     * 服务端封顶：质检后的最终报价不得超过「预估价 × (1 + FINAL_UP_BOUND_RATIO)」，
     * 且必须为正。传入非法值回落为预估价，交由人工兜底而非产生异常金额。
     *
     * @param quotedFen  服务商/运营录入的质检报价（分）
     * @param estimateFen 系统预估价（分）
     * @return 封顶后的最终价（分）
     */
    public static long capFinalAmount(Long quotedFen, long estimateFen) {
        long upBound = (long) (estimateFen * (1 + FINAL_UP_BOUND_RATIO));
        if (quotedFen == null || quotedFen <= 0) {
            return estimateFen;
        }
        // 预估价为 0 时不允许产生正价：直接回落到传入值的安全下界 0，交由上层拦截
        if (estimateFen <= 0) {
            return 0L;
        }
        return Math.min(quotedFen, upBound);
    }

    /**
     * 质检调价跌幅（0~1）。预估价非正时视为 0（无可比基准）。
     */
    public static double dropRatio(long estimateFen, long finalFen) {
        if (estimateFen <= 0) {
            return 0d;
        }
        if (finalFen >= estimateFen) {
            return 0d;
        }
        double d = (double) (estimateFen - finalFen) / (double) estimateFen;
        return Math.min(1d, Math.max(0d, d));
    }

    /**
     * 是否属于异常调价（跌幅超过阈值，需人工复核后再放款，REQ-04）。
     */
    public static boolean abnormalDrop(long estimateFen, long finalFen, double maxDropRatio) {
        return dropRatio(estimateFen, finalFen) > maxDropRatio;
    }
}
