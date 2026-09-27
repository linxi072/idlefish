package com.idlefish.trade.member;

import com.idlefish.trade.member.entity.MemberLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * F-13.2 会员等级纯函数离线单测（无 Spring / 无 MySQL）。
 */
class MemberLevelCalculatorTest {

    private static MemberLevel tier(String code, int min) {
        MemberLevel t = new MemberLevel();
        t.setLevelCode(code);
        t.setMinGrowth(min);
        return t;
    }

    private static List<MemberLevel> tiers() {
        return List.of(tier("L1", 0), tier("L2", 100), tier("L3", 500));
    }

    @Test
    void growthFromTrade() {
        assertEquals(0, MemberLevelCalculator.growthFromTrade(0));
        assertEquals(0, MemberLevelCalculator.growthFromTrade(-100));
        assertEquals(10, MemberLevelCalculator.growthFromTrade(1000));
        assertEquals(9, MemberLevelCalculator.growthFromTrade(999));
        assertEquals(123, MemberLevelCalculator.growthFromTrade(12345));
    }

    @Test
    void growthFromReview() {
        assertEquals(20, MemberLevelCalculator.growthFromReview());
    }

    @Test
    void resolveAtFloor() {
        MemberLevelCalculator.Resolved r = MemberLevelCalculator.resolve(tiers(), 0);
        assertEquals("L1", r.current.getLevelCode());
        assertNotNull(r.next);
        assertEquals("L2", r.next.getLevelCode());
        assertEquals(100, r.growthToNext);
        assertEquals(0, r.percent);
    }

    @Test
    void resolveMidLevel() {
        MemberLevelCalculator.Resolved r = MemberLevelCalculator.resolve(tiers(), 300);
        assertEquals("L2", r.current.getLevelCode());
        assertEquals("L3", r.next.getLevelCode());
        assertEquals(200, r.growthToNext);
        assertEquals(50, r.percent); // (300-100)/(500-100) = 50%
    }

    @Test
    void resolveTopLevel() {
        MemberLevelCalculator.Resolved r = MemberLevelCalculator.resolve(tiers(), 500);
        assertEquals("L3", r.current.getLevelCode());
        assertNull(r.next);
        assertEquals(0, r.growthToNext);
        assertEquals(100, r.percent);
    }

    @Test
    void resolveBeyondTop() {
        MemberLevelCalculator.Resolved r = MemberLevelCalculator.resolve(tiers(), 9999);
        assertEquals("L3", r.current.getLevelCode());
        assertNull(r.next);
        assertEquals(0, r.growthToNext);
        assertEquals(100, r.percent);
    }

    @Test
    void resolveEmptyTiers() {
        MemberLevelCalculator.Resolved r = MemberLevelCalculator.resolve(List.of(), 250);
        assertNull(r.current);
        assertNull(r.next);
        assertEquals(0, r.growthToNext);
        assertEquals(100, r.percent);
    }
}
