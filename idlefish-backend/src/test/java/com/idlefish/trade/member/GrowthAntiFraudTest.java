package com.idlefish.trade.member;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ITER-F07 刷分识别纯函数离线单测（REQ-03 / REQ-06）。
 */
class GrowthAntiFraudTest {

    private static GrowthAntiFraud.Input input(int linked, int maxLinked, int earnCount, int maxEarn,
                                               long amountFen, long minAmount, boolean frozen) {
        return new GrowthAntiFraud.Input(linked, maxLinked, earnCount, maxEarn, amountFen, minAmount, frozen);
    }

    @Test
    void cleanRequestPasses() {
        GrowthAntiFraud.Decision d = GrowthAntiFraud.evaluate(input(0, 3, 0, 20, 5000, 100, false));
        assertFalse(d.blocked());
        assertFalse(d.freeze());
        assertTrue(d.ruleCodes().isEmpty());
    }

    @Test
    void linkedAccountsBlockAndFreeze() {
        GrowthAntiFraud.Decision d = GrowthAntiFraud.evaluate(input(4, 3, 0, 20, 5000, 100, false));
        assertTrue(d.blocked());
        assertTrue(d.freeze());
        assertTrue(d.ruleCodes().contains(GrowthAntiFraud.RULE_LINKED_ACCOUNT));
    }

    @Test
    void frequencyBlocksAtThreshold() {
        // 达到阈值（>=）即拦截，从严
        assertTrue(GrowthAntiFraud.evaluate(input(0, 3, 20, 20, 5000, 100, false)).blocked());
        assertFalse(GrowthAntiFraud.evaluate(input(0, 3, 19, 20, 5000, 100, false)).blocked());
        // 阈值为 0 = 未启用频次规则
        assertFalse(GrowthAntiFraud.evaluate(input(0, 3, 99, 0, 5000, 100, false)).blocked());
    }

    @Test
    void amountThresholdOnlyForTrade() {
        GrowthAntiFraud.Decision d = GrowthAntiFraud.evaluate(input(0, 3, 0, 20, 50, 100, false));
        assertTrue(d.blocked());
        assertTrue(d.ruleCodes().contains(GrowthAntiFraud.RULE_AMOUNT_THRESHOLD));
        // 金额门槛命中不冻结（小额可能是正常低价商品，仅本次不发放）
        assertFalse(d.freeze());
        // amountFen<=0 表示非交易来源（发布/评价），跳过金额门槛
        assertFalse(GrowthAntiFraud.evaluate(input(0, 3, 0, 20, 0, 100, false)).blocked());
        // 恰好等于门槛放行
        assertFalse(GrowthAntiFraud.evaluate(input(0, 3, 0, 20, 100, 100, false)).blocked());
    }

    @Test
    void alreadyFrozenIsFailClosed() {
        GrowthAntiFraud.Decision d = GrowthAntiFraud.evaluate(input(0, 3, 0, 20, 5000, 100, true));
        assertTrue(d.blocked());
        assertTrue(d.freeze());
        assertTrue(d.ruleCodes().contains(GrowthAntiFraud.RULE_ALREADY_FROZEN));
    }

    @Test
    void multipleRulesAggregated() {
        GrowthAntiFraud.Decision d = GrowthAntiFraud.evaluate(input(9, 3, 30, 20, 10, 100, true));
        assertTrue(d.blocked());
        assertTrue(d.freeze());
        assertTrue(d.ruleCodes().contains(GrowthAntiFraud.RULE_ALREADY_FROZEN));
        assertTrue(d.ruleCodes().contains(GrowthAntiFraud.RULE_LINKED_ACCOUNT));
        assertTrue(d.ruleCodes().contains(GrowthAntiFraud.RULE_FREQUENCY));
        assertTrue(d.ruleCodes().contains(GrowthAntiFraud.RULE_AMOUNT_THRESHOLD));
        assertTrue(d.ruleCodesText().contains(","));
    }

    @Test
    void nullInputIsFailClosed() {
        GrowthAntiFraud.Decision d = GrowthAntiFraud.evaluate(null);
        assertTrue(d.blocked());
        assertTrue(d.freeze());
    }

    @Test
    void emptyInputDefaultsPass() {
        GrowthAntiFraud.Decision d = GrowthAntiFraud.evaluate(GrowthAntiFraud.Input.empty());
        assertFalse(d.blocked());
    }
}
