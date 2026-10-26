package com.idlefish.trade.marketing.activity;

import java.util.Random;

/**
 * 砍价金额计算纯函数（ITER-F08 / REQ-08、REQ-12）。
 * <p>
 * 无副作用、可审计：给定当前价、底价、已完成助力数与配置，计算「本次助力应砍金额」。
 * 总额受控——通过约束上限保证后续助力能用最小刀兜底触底（不会砍穿底价，也不会到目标人数仍未触底）。
 */
public final class BargainCalculator {

    private BargainCalculator() {
    }

    /** 是否已触底价。 */
    public static boolean isFloorReached(long currentPriceFen, long floorPriceFen) {
        return currentPriceFen <= floorPriceFen;
    }

    /** 是否已达目标助力人数。 */
    public static boolean isTargetReached(int assistsDone, BargainConfig cfg) {
        return cfg != null && assistsDone >= cfg.getTargetAssistCount();
    }

    /** 砍后价格。 */
    public static long calcFinalPrice(long currentPriceFen, long cutFen) {
        long v = currentPriceFen - cutFen;
        return v < 0 ? 0 : v;
    }

    /**
     * 计算本次助力砍价额（分）。
     *
     * @param currentPriceFen 当前价（分）
     * @param floorPriceFen   底价（分）
     * @param assistsDone     已完成助力数
     * @param cfg             砍价配置
     * @param rnd             随机数源（可注入种子以保证单测可重复）
     * @return 本次砍价额（分），0 表示已触底
     */
    public static long calcNextCut(long currentPriceFen, long floorPriceFen, int assistsDone,
                                   BargainConfig cfg, Random rnd) {
        if (cfg == null || isFloorReached(currentPriceFen, floorPriceFen)) {
            return 0L;
        }
        long remaining = currentPriceFen - floorPriceFen;
        int remainingAssists = cfg.getTargetAssistCount() - assistsDone;
        if (remainingAssists <= 0) {
            // 末刀：直接砍至底价
            return remaining;
        }
        long minCut = Math.min(safe(cfg.getMinCutFen()), remaining);
        long maxCut = Math.min(safe(cfg.getMaxCutFen()), remaining);

        if (!cfg.isPerAssistRandom()) {
            // 均分剩余，并夹在 [min, max] 内
            long even = remaining / remainingAssists;
            return clamp(even, minCut, maxCut);
        }
        // 随机：保证后续剩余刀数能用 minCut 兜底触底
        long maxByFuture = remaining - (long) (remainingAssists - 1) * safe(cfg.getMinCutFen());
        long upper = Math.min(maxCut, maxByFuture);
        long lower = Math.min(minCut, upper);
        if (upper <= lower) {
            return lower;
        }
        long cut = lower + (long) (rnd.nextDouble() * (upper - lower));
        return Math.max(cut, 0L);
    }

    private static long safe(Long v) {
        return v == null || v < 0 ? 0L : v;
    }

    private static long clamp(long v, long lo, long hi) {
        if (v < lo) {
            return lo;
        }
        return Math.min(v, hi);
    }
}
