package com.idlefish.trade.member;

import com.idlefish.trade.member.entity.MemberLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ITER-F07 成长值纯函数离线单测（REQ-06：无 Spring / 无 MySQL / 无副作用）。
 * 覆盖多维累计、变更应用、等级解析、权益门槛与 REQ-05 衰减系列。
 */
class GrowthValueCalculatorTest {

    private static MemberLevel tier(String code, int min) {
        MemberLevel t = new MemberLevel();
        t.setLevelCode(code);
        t.setMinGrowth(min);
        return t;
    }

    private static List<MemberLevel> tiers() {
        return List.of(tier("L1", 0), tier("L2", 100), tier("L3", 500), tier("L4", 2000));
    }

    // ===== REQ-02 多维累计 =====

    @Test
    void fromTrade_scalesByYuanAndCaps() {
        assertEquals(0, GrowthValueCalculator.fromTrade(0, 1, 0));
        assertEquals(0, GrowthValueCalculator.fromTrade(-50, 1, 0));
        assertEquals(0, GrowthValueCalculator.fromTrade(100, 0, 0));       // 比例为 0 不发
        assertEquals(1, GrowthValueCalculator.fromTrade(100, 1, 0));       // 1 元 = 1 点
        assertEquals(12, GrowthValueCalculator.fromTrade(1234, 1, 0));     // 12.34 元 → 12 点（截断）
        assertEquals(5, GrowthValueCalculator.fromTrade(1234, 1, 5));      // 单笔封顶 5
        assertEquals(5, GrowthValueCalculator.fromTrade(1234, 1, 5));
    }

    @Test
    void fixedSources_nonNegative() {
        assertEquals(10, GrowthValueCalculator.fromPublish(10));
        assertEquals(0, GrowthValueCalculator.fromPublish(-5));
        assertEquals(20, GrowthValueCalculator.fromReview(20));
        assertEquals(30, GrowthValueCalculator.fromInspection(30));
        assertEquals(15, GrowthValueCalculator.fromReconcile(15));
    }

    // ===== 变更应用 =====

    @Test
    void applyDelta_floorAndClamp() {
        assertEquals(110, GrowthValueCalculator.applyDelta(100, 10, 0));
        assertEquals(0, GrowthValueCalculator.applyDelta(5, -10, 0));      // 不低于 floor
        assertEquals(90, GrowthValueCalculator.applyDelta(95, -5, 0));
        assertEquals(-10, GrowthValueCalculator.applyDelta(5, -20, -10));  // 自定义 floor
        assertEquals(Integer.MAX_VALUE, GrowthValueCalculator.applyDelta(Integer.MAX_VALUE, 100, 0));
    }

    @Test
    void penalty_cappedAtCurrentAndConfigCap() {
        assertEquals(90, GrowthValueCalculator.penalty(100, 10, 0));
        assertEquals(0, GrowthValueCalculator.penalty(5, 10, 0));          // 绝不为负
        assertEquals(100, GrowthValueCalculator.penalty(100, 0, 500));     // 0 扣减不变
        assertEquals(500, GrowthValueCalculator.penalty(1000, 900, 500));  // 配置封顶 500（min(900,500)）
        assertEquals(100, GrowthValueCalculator.penalty(1000, 900, 0));    // 不封顶按量扣
    }

    // ===== REQ-04 等级解析 =====

    @Test
    void resolveLevelCode_picksHighestSatisfiedTier() {
        assertEquals("L1", GrowthValueCalculator.resolveLevelCode(tiers(), 0, "L1"));
        assertEquals("L2", GrowthValueCalculator.resolveLevelCode(tiers(), 100, "L1"));
        assertEquals("L2", GrowthValueCalculator.resolveLevelCode(tiers(), 499, "L1"));  // 差 1 点不跨阈
        assertEquals("L3", GrowthValueCalculator.resolveLevelCode(tiers(), 500, "L1"));
        assertEquals("L4", GrowthValueCalculator.resolveLevelCode(tiers(), 99999, "L1"));
        assertEquals("LX", GrowthValueCalculator.resolveLevelCode(List.of(), 99999, "LX")); // 空配置回落
        assertEquals("L1", GrowthValueCalculator.resolveLevelCode(tiers(), -5, "L1"));      // 负值回落最低
    }

    @Test
    void crossedTiers_returnsSpanAsc() {
        List<MemberLevel> crossed = GrowthValueCalculator.crossedTiers(tiers(), 90, 2100);
        assertEquals(3, crossed.size());
        assertEquals("L2", crossed.get(0).getLevelCode());
        assertEquals("L3", crossed.get(1).getLevelCode());
        assertEquals("L4", crossed.get(2).getLevelCode());
        assertTrue(GrowthValueCalculator.crossedTiers(tiers(), 100, 200).isEmpty());  // 未跨阈
        assertTrue(GrowthValueCalculator.crossedTiers(tiers(), 500, 100).isEmpty());  // 下降不返回
        assertTrue(GrowthValueCalculator.crossedTiers(null, 0, 100).isEmpty());
    }

    @Test
    void levelChanged_nullSafe() {
        assertTrue(GrowthValueCalculator.levelChanged("L1", "L2"));
        assertFalse(GrowthValueCalculator.levelChanged("L2", "L2"));
        assertTrue(GrowthValueCalculator.levelChanged(null, "L1"));
        assertFalse(GrowthValueCalculator.levelChanged(null, null));
    }

    // ===== REQ-07/12 权益门槛 =====

    @Test
    void levelSatisfied_comparesByThresholdNotLexical() {
        assertTrue(GrowthValueCalculator.levelSatisfied(tiers(), "L3", "L2"));   // 阈值比较
        assertFalse(GrowthValueCalculator.levelSatisfied(tiers(), "L1", "L2"));
        assertTrue(GrowthValueCalculator.levelSatisfied(tiers(), "L1", null));   // 无门槛放行
        assertTrue(GrowthValueCalculator.levelSatisfied(tiers(), "L1", "  "));
        assertFalse(GrowthValueCalculator.levelSatisfied(tiers(), "L1", "L9"));  // 配置缺失从严
        assertFalse(GrowthValueCalculator.levelSatisfied(tiers(), "LX", "L1"));  // 用户等级配置缺失从严
    }

    @Test
    void withinWindow_boundaries() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 23, 12, 0);
        assertTrue(GrowthValueCalculator.withinWindow(now, null, null));
        assertTrue(GrowthValueCalculator.withinWindow(now, now.minusDays(1), now.plusDays(1)));
        assertFalse(GrowthValueCalculator.withinWindow(now, now.plusSeconds(1), null));
        assertFalse(GrowthValueCalculator.withinWindow(now, null, now.minusSeconds(1)));
        assertFalse(GrowthValueCalculator.withinWindow(null, null, null));      // now 为空从严
    }

    // ===== REQ-05 衰减系列 =====

    @Test
    void decayDue_onlyPastThreshold() {
        assertFalse(GrowthValueCalculator.decayDue(90, 90));   // 等于阈值不衰减
        assertFalse(GrowthValueCalculator.decayDue(89, 90));
        assertTrue(GrowthValueCalculator.decayDue(91, 90));
        assertFalse(GrowthValueCalculator.decayDue(999, 0));   // 阈值为 0 = 关闭衰减
        assertFalse(GrowthValueCalculator.decayDue(999, -1));
    }

    @Test
    void decay_accruesPerDayPastThresholdWithFloor() {
        assertEquals(300, GrowthValueCalculator.decay(300, 90, 90, 5, 0));      // 未超阈不变
        assertEquals(250, GrowthValueCalculator.decay(300, 100, 90, 5, 0));     // 超 10 天 ×5
        assertEquals(0, GrowthValueCalculator.decay(30, 200, 90, 5, 0));        // 扣穿到 0
        assertEquals(10, GrowthValueCalculator.decay(30, 200, 90, 5, 10));      // 保底 floor
        assertEquals(300, GrowthValueCalculator.decay(300, 200, 90, 0, 0));     // perDay=0 不衰减
        assertEquals(0, GrowthValueCalculator.decay(-5, 200, 90, 5, 0));        // 负输入钳到 0 再衰减至 floor
    }

    @Test
    void daysUntilDecay_remaining() {
        assertEquals(7, GrowthValueCalculator.daysUntilDecay(83, 90));
        assertEquals(0, GrowthValueCalculator.daysUntilDecay(90, 90));   // 已进入衰减期
        assertEquals(0, GrowthValueCalculator.daysUntilDecay(120, 90));
        assertEquals(0, GrowthValueCalculator.daysUntilDecay(10, 0));    // 关闭衰减
    }

    @Test
    void shouldNotifyDecay_windowAndDedup() {
        assertTrue(GrowthValueCalculator.shouldNotifyDecay(85, 90, 7, false));  // 还剩 5 天，窗内
        assertFalse(GrowthValueCalculator.shouldNotifyDecay(85, 90, 7, true));  // 当日已通知
        assertFalse(GrowthValueCalculator.shouldNotifyDecay(80, 90, 7, false)); // 还剩 10 天，窗外
        assertFalse(GrowthValueCalculator.shouldNotifyDecay(90, 90, 7, false)); // 已进入衰减期
        assertFalse(GrowthValueCalculator.shouldNotifyDecay(85, 90, 0, false)); // 未配置通知天数
    }

    @Test
    void downgraded_isLevelChange() {
        assertTrue(GrowthValueCalculator.downgraded("L3", "L1"));
        assertFalse(GrowthValueCalculator.downgraded("L2", "L2"));
        assertTrue(GrowthValueCalculator.downgraded(null, "L1"));
    }
}
