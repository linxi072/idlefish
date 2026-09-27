package com.idlefish.trade.member;

import com.idlefish.trade.member.entity.MemberLevel;

import java.util.Comparator;
import java.util.List;

/**
 * 会员等级纯函数计算（F-13.2）：成长值 → 等级解析、距下一级进度、成长值获取速率。
 * 与 {@code RiskRuleFunctions} / {@code AnalyticsCalculator} 同范式，离线可单测。
 */
public final class MemberLevelCalculator {

    private MemberLevelCalculator() {
    }

    /**
     * 交易成功获得的成长值：每 1 元（100 分）得 1 点成长值。
     */
    public static int growthFromTrade(long amountFen) {
        if (amountFen <= 0) {
            return 0;
        }
        return (int) (amountFen / 100L);
    }

    /**
     * 评价通过获得的成长值（固定）。
     */
    public static int growthFromReview() {
        return 20;
    }

    /**
     * 根据成长值解析当前等级与下一等级进度。tiers 可为任意顺序（内部按 minGrowth 升序）。
     */
    public static Resolved resolve(List<MemberLevel> tiers, int growth) {
        if (tiers == null || tiers.isEmpty()) {
            return new Resolved(null, null, 0, 100);
        }
        List<MemberLevel> sorted = tiers.stream()
                .sorted(Comparator.comparingInt(t -> t.getMinGrowth() == null ? 0 : t.getMinGrowth()))
                .toList();

        MemberLevel current = sorted.get(0);
        for (MemberLevel t : sorted) {
            if (growth >= (t.getMinGrowth() == null ? 0 : t.getMinGrowth())) {
                current = t;
            } else {
                break;
            }
        }

        MemberLevel next = null;
        for (MemberLevel t : sorted) {
            if ((t.getMinGrowth() == null ? 0 : t.getMinGrowth()) > growth) {
                next = t;
                break;
            }
        }

        int growthToNext = next == null ? 0
                : Math.max((next.getMinGrowth() == null ? 0 : next.getMinGrowth()) - growth, 0);
        int percent = next == null ? 100
                : clampPercent(safePercent(growth - currentMin(current), nextMin(next) - currentMin(current)));
        return new Resolved(current, next, growthToNext, percent);
    }

    private static int currentMin(MemberLevel t) {
        return t.getMinGrowth() == null ? 0 : t.getMinGrowth();
    }

    private static int nextMin(MemberLevel t) {
        return t.getMinGrowth() == null ? 0 : t.getMinGrowth();
    }

    private static int safePercent(long part, long whole) {
        if (whole <= 0) {
            return 0;
        }
        return (int) (part * 100L / whole);
    }

    private static int clampPercent(int p) {
        return Math.max(0, Math.min(100, p));
    }

    /**
     * 等级解析结果。
     */
    public static final class Resolved {
        public final MemberLevel current;   // 当前等级（顶级时即最高级）
        public final MemberLevel next;      // 下一级（已顶级为 null）
        public final int growthToNext;      // 距下一级还需成长值（顶级为 0）
        public final int percent;           // 当前级内进度百分比 0~100（顶级为 100）

        public Resolved(MemberLevel current, MemberLevel next, int growthToNext, int percent) {
            this.current = current;
            this.next = next;
            this.growthToNext = growthToNext;
            this.percent = percent;
        }
    }
}
