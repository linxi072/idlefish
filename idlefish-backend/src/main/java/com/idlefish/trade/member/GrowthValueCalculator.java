package com.idlefish.trade.member;

import com.idlefish.trade.member.entity.MemberLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 成长值纯函数内核（ITER-F07 / PRD-P2-F07 REQ-02 / REQ-06）。
 * <p>
 * 与 {@link MemberLevelCalculator} / {@code RiskRuleFunctions} / {@code AnalyticsCalculator} 同范式：
 * 全部为无副作用静态方法，不访问数据库 / 外部服务 / Spring 上下文，保证离线 Mockito 单测可
 * 达到 100% 分支覆盖（REQ-06 / 目标 G2）。
 * </p>
 * <p><b>职责边界（REQ-01）</b>：本类只负责「成长值」——不可消耗、仅决定等级与权益档位；
 * 可消耗的「积分」由 {@code marketing.service.PointCalculator} 独立演进，两者绝不互相折算。</p>
 * <p><b>规则版本（REQ-14）</b>：所有计算只作用于新行为；历史成长值不做追溯重算，
 * 由调用方在落流水时快照 {@code ruleVersion} 以便对账。</p>
 */
public final class GrowthValueCalculator {

    private GrowthValueCalculator() {
    }

    // ===== REQ-02 多维累计：交易 / 发布 / 评价 / 验货配合 / 纠纷和解 =====

    /**
     * 交易成功：每 1 元（100 分）得 {@code growthPerYuanFen} 点成长值，单笔封顶 {@code capPerOrder}（&le;0 不封顶）。
     */
    public static int fromTrade(long amountFen, int growthPerYuanFen, int capPerOrder) {
        if (amountFen <= 0 || growthPerYuanFen <= 0) {
            return 0;
        }
        long v = amountFen * growthPerYuanFen / 100L;
        if (capPerOrder > 0 && v > capPerOrder) {
            v = capPerOrder;
        }
        return toInt(v);
    }

    /** 发布商品：固定成长值（非负）。 */
    public static int fromPublish(int fixed) {
        return Math.max(0, fixed);
    }

    /** 评价通过：固定成长值（非负）。 */
    public static int fromReview(int fixed) {
        return Math.max(0, fixed);
    }

    /** 验货配合（F-02 验货单完成）：固定成长值（非负）。 */
    public static int fromInspection(int fixed) {
        return Math.max(0, fixed);
    }

    /** 纠纷和解（F-17 判责且双方和解）：固定成长值（非负）。 */
    public static int fromReconcile(int fixed) {
        return Math.max(0, fixed);
    }

    // ===== 变更应用（含下限保护）=====

    /**
     * 应用成长值增量：正为获得、负为扣减；结果不低于 {@code floor}（默认 0，杜绝负成长值）。
     * 返回扣减/增加后的新值，纯函数不改输入。
     */
    public static int applyDelta(int current, int delta, int floor) {
        int base = Math.max(0, current);
        long next = (long) base + delta;
        if (next < floor) {
            next = floor;
        }
        if (next > Integer.MAX_VALUE) {
            next = Integer.MAX_VALUE;
        }
        return (int) next;
    }

    /** F-17 判责扣减（REQ-10）：扣减量不超过当前成长值，且绝不低于 0。 */
    public static int penalty(int current, int amount, int cap) {
        if (amount <= 0) {
            return Math.max(0, current);
        }
        int base = Math.max(0, current);
        int cut = cap > 0 ? Math.min(amount, cap) : amount;
        return Math.max(0, base - cut);
    }

    // ===== REQ-04 等级解析与跨阈值识别 =====

    /**
     * 按成长值解析等级编码；tiers 为空或全部阈值高于成长值时回落 {@code fallback}。
     */
    public static String resolveLevelCode(List<MemberLevel> tiers, int growth, String fallback) {
        if (tiers == null || tiers.isEmpty()) {
            return fallback;
        }
        String code = null;
        int best = Integer.MIN_VALUE;
        for (MemberLevel t : tiers) {
            int min = t.getMinGrowth() == null ? 0 : t.getMinGrowth();
            if (growth >= min && min >= best) {
                best = min;
                code = t.getLevelCode();
            }
        }
        return code == null ? fallback : code;
    }

    /**
     * 识别一次变更是否跨过等级阈值（REQ-04 跨阈值即时升级）：返回被跨越的等级配置，
     * 按阈值升序；未跨越返回空列表。跨级（一次跳多级）时全部返回，便于逐级埋点与通知。
     */
    public static List<MemberLevel> crossedTiers(List<MemberLevel> tiers, int before, int after) {
        List<MemberLevel> out = new ArrayList<>();
        if (tiers == null || tiers.isEmpty() || after <= before) {
            return out;
        }
        List<MemberLevel> sorted = new ArrayList<>(tiers);
        sorted.sort((a, b) -> Integer.compare(minOf(a), minOf(b)));
        for (MemberLevel t : sorted) {
            int min = minOf(t);
            if (min > before && min <= after) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * 跨阈值后是否发生等级编码变化（升级或降级）。用于「是否落 level 流水 / 埋点」判定。
     */
    public static boolean levelChanged(String beforeCode, String afterCode) {
        if (beforeCode == null || afterCode == null) {
            return beforeCode != afterCode;
        }
        return !beforeCode.equals(afterCode);
    }

    // ===== 权益兑换门槛（REQ-07 / REQ-12）=====

    /**
     * 等级门槛校验（REQ-07）：用户当前等级是否达到权益要求的最低等级。
     * <p>按等级阈值比较而非编码字符串比较（避免 L10 &lt; L2 的字典序陷阱）；
     * 任一等级未在配置中时从严处理（视为不满足），防止配置疏漏导致越权兑换。</p>
     */
    public static boolean levelSatisfied(List<MemberLevel> tiers, String userLevelCode, String requiredLevelCode) {
        if (requiredLevelCode == null || requiredLevelCode.isBlank()) {
            return true;
        }
        int required = minGrowthOfCode(tiers, requiredLevelCode);
        if (required == Integer.MIN_VALUE) {
            return false;
        }
        int current = minGrowthOfCode(tiers, userLevelCode);
        if (current == Integer.MIN_VALUE) {
            return false;
        }
        return current >= required;
    }

    /**
     * 权益上架时间窗校验（REQ-12）：当前时间需落在 [onlineAt, offlineAt) 内；
     * 边界为空表示不限制该侧。
     */
    public static boolean withinWindow(java.time.LocalDateTime now,
                                       java.time.LocalDateTime onlineAt,
                                       java.time.LocalDateTime offlineAt) {
        if (now == null) {
            return false;
        }
        if (onlineAt != null && now.isBefore(onlineAt)) {
            return false;
        }
        return offlineAt == null || !now.isAfter(offlineAt);
    }

    private static int minGrowthOfCode(List<MemberLevel> tiers, String code) {
        if (tiers == null || tiers.isEmpty() || code == null) {
            return Integer.MIN_VALUE;
        }
        for (MemberLevel t : tiers) {
            if (code.equals(t.getLevelCode())) {
                return minOf(t);
            }
        }
        return Integer.MIN_VALUE;
    }

    // ===== REQ-05 不活跃衰减 / 降级 / 提前通知 =====

    /**
     * 衰减天数是否已达阈值：{@code inactiveDays}（距最后活跃的天数）超过 {@code thresholdDays} 才衰减，
     * 避免把「刚注册未交易」的用户误判为流失。
     */
    public static boolean decayDue(long inactiveDays, int thresholdDays) {
        return thresholdDays > 0 && inactiveDays > thresholdDays;
    }

    /**
     * 计算衰减后的成长值：每过一个衰减日扣 {@code decayPerDay}，结果不低于 {@code floor}（默认 0）。
     * 仅计算不落库，由调度任务决定执行。
     */
    public static int decay(int growth, long inactiveDays, int thresholdDays, int decayPerDay, int floor) {
        int base = Math.max(0, growth);
        if (!decayDue(inactiveDays, thresholdDays) || decayPerDay <= 0) {
            return base;
        }
        long days = inactiveDays - thresholdDays;
        long cut = days * decayPerDay;
        long next = base - cut;
        return (int) Math.max(floor, next);
    }

    /**
     * 距进入衰减还差几天（REQ-05 提前通知）：{@code <=0} 表示已进入或已过衰减期，无需再通知。
     */
    public static int daysUntilDecay(long inactiveDays, int thresholdDays) {
        if (thresholdDays <= 0) {
            return 0;
        }
        long left = thresholdDays - inactiveDays;
        if (left <= 0) {
            return 0;
        }
        return (int) Math.min(left, Integer.MAX_VALUE);
    }

    /**
     * 是否应发送「即将衰减」提前通知（REQ-05 提前 7 日）：
     * 距衰减日 {@code daysUntilDecay} 落在 (0, {@code notifyDays}] 内，且当日尚未通知过。
     */
    public static boolean shouldNotifyDecay(long inactiveDays, int thresholdDays, int notifyDays,
                                            boolean notifiedToday) {
        if (notifiedToday) {
            return false;
        }
        int left = daysUntilDecay(inactiveDays, thresholdDays);
        return left > 0 && notifyDays > 0 && left <= notifyDays;
    }

    /**
     * 衰减后等级是否下调（REQ-05 降级）：比较前后等级编码即可，编码不同即视为档位变动。
     */
    public static boolean downgraded(String beforeCode, String afterCode) {
        return levelChanged(beforeCode, afterCode);
    }

    // ===== 内部 =====

    private static int minOf(MemberLevel t) {
        return t.getMinGrowth() == null ? 0 : t.getMinGrowth();
    }

    private static int toInt(long v) {
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    }
}
