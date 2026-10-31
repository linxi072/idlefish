package com.idlefish.trade.marketing.service;

import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.marketing.entity.Coupon;
import com.idlefish.trade.marketing.entity.UserCoupon;
import com.idlefish.trade.marketing.vo.CouponVO;
import com.idlefish.trade.marketing.vo.UserCouponVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CouponViews 纯函数单测（F-10 续拆）：折扣计算 / 适用范围 / VO 映射。
 * 无 Mock、无 Spring，离线可跑（mvn -o -P!micrometer test）。
 */
class CouponViewsTest {

    private Coupon coupon(String type, long threshold, long reduce, Double rate, Long cap, String scope, Long scopeId) {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setName("测试券");
        c.setType(type);
        c.setThresholdAmount(threshold);
        c.setReduceAmount(reduce);
        c.setDiscountRate(rate);
        c.setMaxDiscountAmount(cap);
        c.setScope(scope);
        c.setScopeId(scopeId);
        c.setStatus(CouponService.STATUS_ACTIVE);
        c.setStartAt(LocalDateTime.now().minusDays(1));
        c.setEndAt(LocalDateTime.now().plusDays(1));
        c.setTotalCount(100);
        c.setClaimedCount(0);
        c.setPerUserLimit(1);
        return c;
    }

    // ---------- 折扣计算 ----------

    @Test
    @DisplayName("满减：达到门槛返回减免，未达返回 0")
    void fullReduction() {
        Coupon c = coupon(CouponService.TYPE_FULL_REDUCTION, 10000, 2000, 1.0, 0L, CouponService.SCOPE_ALL, null);
        assertEquals(2000, CouponViews.calculateDiscount(c, 15000));
        assertEquals(0, CouponViews.calculateDiscount(c, 5000));
    }

    @Test
    @DisplayName("无门槛：直接返回减免，且不超过商品金额")
    void noThreshold() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        assertEquals(500, CouponViews.calculateDiscount(c, 5000));
        assertEquals(300, CouponViews.calculateDiscount(c, 300));
    }

    @Test
    @DisplayName("折扣：9折封顶30元")
    void discountWithCap() {
        Coupon c = coupon(CouponService.TYPE_DISCOUNT, 0, 0, 0.9, 3000L, CouponService.SCOPE_ALL, null);
        assertEquals(3000, CouponViews.calculateDiscount(c, 100000));
        assertEquals(500, CouponViews.calculateDiscount(c, 5000));
    }

    @Test
    @DisplayName("折扣：rate=1.0 不打折返回 0")
    void discountRateOne() {
        Coupon c = coupon(CouponService.TYPE_DISCOUNT, 0, 0, 1.0, 0L, CouponService.SCOPE_ALL, null);
        assertEquals(0, CouponViews.calculateDiscount(c, 5000));
    }

    @Test
    @DisplayName("null 券或金额<=0 返回 0")
    void nullOrNonPositive() {
        assertEquals(0, CouponViews.calculateDiscount(null, 100));
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        assertEquals(0, CouponViews.calculateDiscount(c, 0));
        assertEquals(0, CouponViews.calculateDiscount(c, -10));
    }

    @Test
    @DisplayName("未知类型返回 0")
    void unknownType() {
        Coupon c = coupon("MYSTERY", 0, 100, 1.0, 0L, CouponService.SCOPE_ALL, null);
        assertEquals(0, CouponViews.calculateDiscount(c, 1000));
    }

    @Test
    @DisplayName("满减减免封顶不超过商品金额")
    void fullReductionCappedByGoods() {
        Coupon c = coupon(CouponService.TYPE_FULL_REDUCTION, 100, 5000, 1.0, 0L, CouponService.SCOPE_ALL, null);
        assertEquals(300, CouponViews.calculateDiscount(c, 300));
    }

    // ---------- 适用范围 ----------

    @Test
    @DisplayName("ALL 适用范围恒成立（含 null 商品）")
    void scopeAll() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        assertTrue(CouponViews.isScopeApplicable(c, null));
        assertTrue(CouponViews.isScopeApplicable(c, new Item()));
    }

    @Test
    @DisplayName("CATEGORY：类目命中/未命中/缺商品/缺scopeId")
    void scopeCategory() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_CATEGORY, 100L);
        Item item = new Item();
        item.setCategoryId(100L);
        assertTrue(CouponViews.isScopeApplicable(c, item));
        Item other = new Item();
        other.setCategoryId(200L);
        assertFalse(CouponViews.isScopeApplicable(c, other));
        assertFalse(CouponViews.isScopeApplicable(c, null));
        Coupon noId = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_CATEGORY, null);
        assertFalse(CouponViews.isScopeApplicable(noId, item));
    }

    @Test
    @DisplayName("ITEM：商品命中/未命中")
    void scopeItem() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ITEM, 9001L);
        Item item = new Item();
        item.setId(9001L);
        assertTrue(CouponViews.isScopeApplicable(c, item));
        Item other = new Item();
        other.setId(9002L);
        assertFalse(CouponViews.isScopeApplicable(c, other));
    }

    @Test
    @DisplayName("未知范围返回 false")
    void scopeUnknown() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, "ORBIT", 1L);
        assertFalse(CouponViews.isScopeApplicable(c, new Item()));
    }

    // ---------- toCenterVO ----------

    @Test
    @DisplayName("toCenterVO 字段映射正确")
    void toCenterVOBasic() {
        Coupon c = coupon(CouponService.TYPE_DISCOUNT, 0, 0, 0.9, 3000L, CouponService.SCOPE_ALL, null);
        c.setTotalCount(100);
        c.setClaimedCount(10);
        CouponVO vo = CouponViews.toCenterVO(c, 2001L, Collections.emptySet());
        assertEquals(1L, vo.getId());
        assertEquals("测试券", vo.getName());
        assertEquals(CouponService.TYPE_DISCOUNT, vo.getType());
        assertEquals(3000L, vo.getMaxDiscountAmount());
    }

    @Test
    @DisplayName("toCenterVO claimed 标记")
    void toCenterVOClaimed() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        Set<Long> ids = new HashSet<>();
        ids.add(c.getId());
        assertTrue(CouponViews.toCenterVO(c, 2001L, ids).getClaimed());
        assertFalse(CouponViews.toCenterVO(c, null, ids).getClaimed());
        assertFalse(CouponViews.toCenterVO(c, 2001L, Collections.emptySet()).getClaimed());
    }

    @Test
    @DisplayName("toCenterVO claimable 综合判定（库存/时间/状态）")
    void toCenterVOClaimable() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        c.setStatus(CouponService.STATUS_ACTIVE);
        c.setEndAt(LocalDateTime.now().plusDays(1));
        c.setTotalCount(100);
        c.setClaimedCount(10);
        assertTrue(CouponViews.toCenterVO(c, 2001L, Collections.emptySet()).getClaimable());

        Coupon sold = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        sold.setStatus(CouponService.STATUS_ACTIVE);
        sold.setEndAt(LocalDateTime.now().plusDays(1));
        sold.setTotalCount(100);
        sold.setClaimedCount(100);
        assertFalse(CouponViews.toCenterVO(sold, 2001L, Collections.emptySet()).getClaimable());

        Coupon ended = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        ended.setStatus(CouponService.STATUS_ACTIVE);
        ended.setEndAt(LocalDateTime.now().minusDays(1));
        ended.setTotalCount(100);
        ended.setClaimedCount(10);
        assertFalse(CouponViews.toCenterVO(ended, 2001L, Collections.emptySet()).getClaimable());

        Coupon paused = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        paused.setStatus("PAUSED");
        paused.setEndAt(LocalDateTime.now().plusDays(1));
        paused.setTotalCount(100);
        paused.setClaimedCount(10);
        assertFalse(CouponViews.toCenterVO(paused, 2001L, Collections.emptySet()).getClaimable());
    }

    // ---------- toUserVO ----------

    @Test
    @DisplayName("toUserVO 携带券模板字段")
    void toUserVOWithCoupon() {
        Coupon c = coupon(CouponService.TYPE_DISCOUNT, 0, 0, 0.9, 3000L, CouponService.SCOPE_ALL, null);
        Map<Long, Coupon> map = new HashMap<>();
        map.put(c.getId(), c);
        UserCoupon uc = new UserCoupon();
        uc.setId(9L);
        uc.setCouponId(c.getId());
        uc.setStatus("UNUSED");
        uc.setOrderNo("NO1");
        UserCouponVO vo = CouponViews.toUserVO(uc, map);
        assertEquals(9L, vo.getId());
        assertEquals(c.getId(), vo.getCouponId());
        assertEquals("UNUSED", vo.getStatus());
        assertEquals("NO1", vo.getOrderNo());
        assertEquals("测试券", vo.getName());
        assertEquals(CouponService.TYPE_DISCOUNT, vo.getType());
        assertEquals(3000L, vo.getMaxDiscountAmount());
    }

    @Test
    @DisplayName("toUserVO 券模板缺失时仅基础字段")
    void toUserVOMissingCoupon() {
        UserCoupon uc = new UserCoupon();
        uc.setId(9L);
        uc.setCouponId(99L);
        uc.setStatus("UNUSED");
        UserCouponVO vo = CouponViews.toUserVO(uc, Collections.emptyMap());
        assertEquals(9L, vo.getId());
        assertNull(vo.getName());
        assertNull(vo.getType());
    }
}
