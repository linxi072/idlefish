package com.idlefish.trade.risk.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 异常评分卡纯函数单测（F-15.1）：验证 low/mid/high 三档聚合逻辑。
 */
class RiskScorerTest {

    private RiskScorer.RiskSignals sig(int openRisk, boolean newDev, int bind, boolean bl,
                                       long amount, long hiThreshold, int devThreshold) {
        return new RiskScorer.RiskSignals(openRisk, newDev, bind, bl, amount, hiThreshold, devThreshold);
    }

    @Test
    @DisplayName("全低信号 → low")
    void allLow() {
        RiskScorer.RiskBand b = RiskScorer.score(sig(0, false, 0, false, 0, 5_000_000, 3));
        assertEquals("low", b.getBand());
        assertEquals(0, b.getScore());
    }

    @Test
    @DisplayName("设备黑名单直接高危 high")
    void blacklistHigh() {
        RiskScorer.RiskBand b = RiskScorer.score(sig(0, false, 0, true, 0, 5_000_000, 3));
        assertEquals("high", b.getBand());
        assertEquals(60, b.getScore());
    }

    @Test
    @DisplayName("新设备+超阈值金额 → +35，单信号仅 mid")
    void newDeviceHighAmount() {
        // 35 分 < 60 分阈值，单信号为 mid
        RiskScorer.RiskBand b = RiskScorer.score(sig(0, true, 0, false, 5_000_000, 5_000_000, 3));
        assertEquals("mid", b.getBand());
        assertEquals(35, b.getScore());
    }

    @Test
    @DisplayName("新设备+超阈值金额 + 多账号 → 60 分 high")
    void combinedHigh() {
        RiskScorer.RiskBand b = RiskScorer.score(sig(0, true, 3, false, 5_000_000, 5_000_000, 3));
        assertEquals("high", b.getBand());
        assertEquals(60, b.getScore()); // 35 + 25
    }

    @Test
    @DisplayName("未处置事件累计（封顶 3 个）：3 个=45 mid，4 个仍=45（封顶）")
    void openRiskCap() {
        assertEquals(45, RiskScorer.score(sig(3, false, 0, false, 0, 5_000_000, 3)).getScore());
        assertEquals(45, RiskScorer.score(sig(10, false, 0, false, 0, 5_000_000, 3)).getScore());
        assertEquals("mid", RiskScorer.score(sig(3, false, 0, false, 0, 5_000_000, 3)).getBand());
    }

    @Test
    @DisplayName("多账号达阈值即 mid（+25 未到 30 阈值时仍 low，叠加 open 事件到 mid）")
    void multiAccountMid() {
        // 仅多账号 25 分 < 30 → low；叠加 1 个 open(15) → 40 mid
        assertEquals("low", RiskScorer.score(sig(0, false, 3, false, 0, 5_000_000, 3)).getBand());
        assertEquals("mid", RiskScorer.score(sig(1, false, 3, false, 0, 5_000_000, 3)).getBand());
    }
}
