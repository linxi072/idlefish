package com.idlefish.trade.marketing.activity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/**
 * 抽奖概率抽取纯函数（ITER-F08 / REQ-09、REQ-12）。
 * <p>
 * 确定性可审计：给定奖品池与随机数 r∈[0,1)，按累计概率返回中奖奖品或 null（未中奖）。
 * 与随机源解耦，便于单测复现；中奖后由服务层原子扣减奖品库存并留存配置概率快照（REQ-09）。
 */
public final class LotteryDrawer {

    private LotteryDrawer() {
    }

    /** 概率合法性：非空、各概率 >=0、总和 <=1。 */
    public static boolean validateProbabilities(List<LotteryPrize> prizes) {
        if (prizes == null || prizes.isEmpty()) {
            return false;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (LotteryPrize p : prizes) {
            if (p.getProbability() == null || p.getProbability().signum() < 0) {
                return false;
            }
            sum = sum.add(p.getProbability());
        }
        return sum.compareTo(BigDecimal.ONE) <= 0;
    }

    /** 累计概率抽取：r∈[0,1)，命中返回中奖奖品，否则返回 null（未中奖）。 */
    public static LotteryPrize draw(List<LotteryPrize> prizes, double r) {
        if (prizes == null || r < 0.0 || r >= 1.0) {
            return null;
        }
        double cursor = 0.0;
        for (LotteryPrize p : prizes) {
            if (p.getProbability() == null) {
                continue;
            }
            cursor += p.getProbability().doubleValue();
            if (r < cursor) {
                return p;
            }
        }
        return null;
    }

    /** 基于随机源的抽取（包装 {@link #draw(List, double)}）。 */
    public static LotteryPrize draw(List<LotteryPrize> prizes, Random rnd) {
        return draw(prizes, rnd.nextDouble());
    }
}
