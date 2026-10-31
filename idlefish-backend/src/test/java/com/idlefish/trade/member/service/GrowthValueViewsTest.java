package com.idlefish.trade.member.service;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.member.entity.GrowthValueLog;
import com.idlefish.trade.member.entity.MemberLevel;
import com.idlefish.trade.member.entity.UserGrowth;
import com.idlefish.trade.member.vo.GrowthAccountAdminVO;
import com.idlefish.trade.member.vo.GrowthAccountVO;
import com.idlefish.trade.member.vo.GrowthValueLogVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 成长值视图装配（GrowthValueViews）纯函数离线单测：无 Mock、无 MySQL。
 * 锁定 VO 映射、valueOf / inactiveDaysOf / safe 等外提逻辑与重构前一致。
 */
class GrowthValueViewsTest {

    private static IdlefishProperties.Growth growth() {
        return new IdlefishProperties.Growth();
    }

    private static List<MemberLevel> tiers() {
        MemberLevel l1 = new MemberLevel();
        l1.setLevelCode("L1");
        l1.setLevelName("注册会员");
        l1.setMinGrowth(0);
        MemberLevel l2 = new MemberLevel();
        l2.setLevelCode("L2");
        l2.setLevelName("青铜会员");
        l2.setMinGrowth(100);
        l2.setIcon("ic2");
        l2.setColor("#cccccc");
        l2.setFreeShipping(1);
        l2.setPriorityReview(0);
        l2.setCommissionDiscount(50);
        return List.of(l1, l2);
    }

    private static UserGrowth account(int gv, String levelCode, int frozen) {
        UserGrowth g = new UserGrowth();
        g.setUserId(1L);
        g.setGrowthValue(gv);
        g.setTotalEarned(gv);
        g.setLevelCode(levelCode);
        g.setGrowthFrozen(frozen);
        g.setLastActiveAt(LocalDateTime.now().minusDays(10));
        return g;
    }

    @Test
    void valueOf_handlesNullGrowth() {
        UserGrowth g = new UserGrowth();
        g.setGrowthValue(null);
        assertEquals(0, GrowthValueViews.valueOf(g));
        g.setGrowthValue(123);
        assertEquals(123, GrowthValueViews.valueOf(g));
    }

    @Test
    void inactiveDaysOf_fallsBackToCreatedAt() {
        assertEquals(0, GrowthValueViews.inactiveDaysOf(null));
        UserGrowth g = new UserGrowth();
        g.setCreatedAt(LocalDateTime.now().minusDays(100));
        assertTrue(GrowthValueViews.inactiveDaysOf(g) >= 99);
    }

    @Test
    void safe_truncatesAndGuards() {
        assertEquals("", GrowthValueViews.safe(null));
        assertEquals("", GrowthValueViews.safe("   "));
        String longStr = "x".repeat(600);
        assertEquals(480, GrowthValueViews.safe(longStr).length());
        assertEquals("ok", GrowthValueViews.safe("ok"));
    }

    @Test
    void toVO_mapsAllFields() {
        GrowthValueLog l = new GrowthValueLog();
        l.setId(7L);
        l.setUserId(1L);
        l.setBizType("EARN_TRADE");
        l.setBizId("ORD1");
        l.setDelta(120);
        l.setBeforeValue(0);
        l.setAfterValue(120);
        l.setLevelCode("L1");
        l.setRuleVersion("v1");
        l.setRemark("r");
        l.setCreatedAt(LocalDateTime.now());
        GrowthValueLogVO v = GrowthValueViews.toVO(l);
        assertEquals(7L, v.getId());
        assertEquals("EARN_TRADE", v.getBizType());
        assertEquals(120, v.getDelta());
        assertEquals(0, v.getBeforeValue());
        assertEquals(120, v.getAfterValue());
        assertEquals("L1", v.getLevelCode());
        assertEquals("v1", v.getRuleVersion());
    }

    @Test
    void toAccountVO_prefersStoredLevel() {
        UserGrowth g = account(300, "L2", 0);
        GrowthAccountVO v = GrowthValueViews.toAccountVO(g, tiers(), growth(), true);
        assertEquals("L2", v.getLevelCode());
        assertEquals(300, v.getGrowthValue());
        assertEquals("青铜会员", v.getLevelName());
        assertEquals(1, v.getFreeShipping());
        assertEquals(50, (int) v.getCommissionDiscount());
        assertEquals(false, v.getFrozen());
        assertTrue(v.getDaysUntilDecay() > 0);
    }

    @Test
    void toAccountVO_resolvesWhenStoredMissing() {
        UserGrowth g = account(300, null, 0);
        GrowthAccountVO v = GrowthValueViews.toAccountVO(g, tiers(), growth(), false);
        assertEquals("L2", v.getLevelCode());
        assertEquals("青铜会员", v.getLevelName());
    }

    @Test
    void toAdminVO_derivesLevelNameAndDecay() {
        UserGrowth g = account(300, "L2", 0);
        GrowthAccountAdminVO v = GrowthValueViews.toAdminVO(g, tiers(), growth());
        assertEquals(1L, v.getUserId());
        assertEquals("L2", v.getLevelCode());
        assertEquals("青铜会员", v.getLevelName());
        assertEquals(300, v.getGrowthValue());
        assertEquals(false, v.getFrozen());
        assertTrue(v.getDaysUntilDecay() > 0);
    }

    @Test
    void toAdminVO_missingLevelNameYieldsNull() {
        UserGrowth g = account(300, "L9", 0);
        GrowthAccountAdminVO v = GrowthValueViews.toAdminVO(g, tiers(), growth());
        assertNull(v.getLevelName());
    }
}
