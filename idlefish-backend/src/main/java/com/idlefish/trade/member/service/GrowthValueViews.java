package com.idlefish.trade.member.service;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.DateTimeUtil;
import com.idlefish.trade.member.GrowthValueCalculator;
import com.idlefish.trade.member.MemberLevelCalculator;
import com.idlefish.trade.member.entity.GrowthValueLog;
import com.idlefish.trade.member.entity.MemberLevel;
import com.idlefish.trade.member.entity.UserGrowth;
import com.idlefish.trade.member.vo.GrowthAccountAdminVO;
import com.idlefish.trade.member.vo.GrowthAccountVO;
import com.idlefish.trade.member.vo.GrowthValueLogVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成长值视图装配（纯函数，无副作用）：将领域实体与等级配置映射为用户端 / 后台 / 流水 VO。
 * 从 {@link GrowthValueService} 外提，隔离展示层映射，便于离线单测且不影响发放 / 扣减等核心逻辑。
 */
final class GrowthValueViews {

    private GrowthValueViews() {
    }

    /** 成长值字符串安全截断（写入流水 remark 前，避免超长备注撑爆字段）。 */
    static String safe(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.length() > 480 ? s.substring(0, 480) : s;
    }

    static int valueOf(UserGrowth g) {
        return g.getGrowthValue() == null ? 0 : g.getGrowthValue();
    }

    static long inactiveDaysOf(UserGrowth g) {
        if (g == null) {
            return 0;
        }
        LocalDateTime last = g.getLastActiveAt() != null ? g.getLastActiveAt() : g.getCreatedAt();
        if (last == null) {
            return 0;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(last, LocalDateTime.now());
        return Math.max(0, days);
    }

    static GrowthValueLogVO toVO(GrowthValueLog l) {
        GrowthValueLogVO v = new GrowthValueLogVO();
        v.setId(l.getId());
        v.setUserId(l.getUserId());
        v.setBizType(l.getBizType());
        v.setBizId(l.getBizId());
        v.setDelta(l.getDelta());
        v.setBeforeValue(l.getBeforeValue());
        v.setAfterValue(l.getAfterValue());
        v.setLevelCode(l.getLevelCode());
        v.setRuleVersion(l.getRuleVersion());
        v.setRemark(l.getRemark());
        v.setCreatedAt(l.getCreatedAt() == null ? null : l.getCreatedAt().format(DateTimeUtil.FMT));
        return v;
    }

    /**
     * 账户 → 用户端 / 后台共用视图：等级解析走 MemberLevelCalculator（与 F-13.2 同口径）。
     *
     * @param preferStoredLevel true（用户端）：展示已生效 level_code，与等级通知口径一致；
     *                          false（后台只读）：直接以解析结果为准，避免展示尚未重算的滞后编码
     */
    static GrowthAccountVO toAccountVO(UserGrowth g, List<MemberLevel> tiers,
                                       IdlefishProperties.Growth growth, boolean preferStoredLevel) {
        int gv = valueOf(g);
        MemberLevelCalculator.Resolved r = MemberLevelCalculator.resolve(tiers, gv);
        GrowthAccountVO v = new GrowthAccountVO();
        String code = preferStoredLevel && g.getLevelCode() != null ? g.getLevelCode()
                : (r.current == null ? g.getLevelCode() : r.current.getLevelCode());
        v.setLevelCode(code);
        v.setGrowthValue(gv);
        v.setTotalEarned(g.getTotalEarned() == null ? 0 : g.getTotalEarned());
        if (r.current != null) {
            v.setLevelName(r.current.getLevelName());
            v.setIcon(r.current.getIcon());
            v.setColor(r.current.getColor());
            v.setFreeShipping(r.current.getFreeShipping());
            v.setPriorityReview(r.current.getPriorityReview());
            v.setCommissionDiscount(r.current.getCommissionDiscount());
        }
        v.setNextLevelName(r.next == null ? null : r.next.getLevelName());
        v.setGrowthToNext(r.growthToNext);
        v.setPercent(r.percent);
        v.setFrozen(g.isFrozen());
        v.setDaysUntilDecay(GrowthValueCalculator.daysUntilDecay(inactiveDaysOf(g), growth.getDecayThresholdDays()));
        v.setDecayNotifyDays(growth.getDecayNotifyDays());
        v.setRuleVersion(growth.getRuleVersion());
        return v;
    }

    /** 账户 → 后台列表视图（派生等级名称与距衰减天数）。 */
    static GrowthAccountAdminVO toAdminVO(UserGrowth g, List<MemberLevel> tiers, IdlefishProperties.Growth growth) {
        GrowthAccountAdminVO v = new GrowthAccountAdminVO();
        v.setUserId(g.getUserId());
        v.setLevelCode(g.getLevelCode());
        v.setGrowthValue(valueOf(g));
        v.setTotalEarned(g.getTotalEarned() == null ? 0 : g.getTotalEarned());
        v.setFrozen(g.isFrozen());
        v.setLastActiveAt(g.getLastActiveAt() == null ? null : g.getLastActiveAt().format(DateTimeUtil.FMT));
        v.setDaysUntilDecay(GrowthValueCalculator.daysUntilDecay(inactiveDaysOf(g), growth.getDecayThresholdDays()));
        String name = tiers.stream()
                .filter(t -> t.getLevelCode() != null && t.getLevelCode().equals(g.getLevelCode()))
                .map(MemberLevel::getLevelName)
                .findFirst()
                .orElse(null);
        v.setLevelName(name);
        return v;
    }
}
