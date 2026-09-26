package com.idlefish.trade.marketing.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.marketing.entity.Coupon;
import com.idlefish.trade.marketing.entity.UserCoupon;
import com.idlefish.trade.marketing.mapper.CouponMapper;
import com.idlefish.trade.marketing.mapper.UserCouponMapper;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.notify.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 优惠券服务纯逻辑单测（F-10）：折扣计算 / 领取规则 / 核销 / 释放。
 * 全程 Mock Mapper，无 Spring、无 DB，离线可跑（mvn -o -Plocal test）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponServiceTest {

    @Mock private CouponMapper couponMapper;
    @Mock private UserCouponMapper userCouponMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private NotificationService notificationService;
    @Mock private MetricsRegistry metrics;
    @InjectMocks private CouponService service;

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

    // ---------- 折扣计算（纯函数）----------

    @Test
    @DisplayName("满减：达到门槛返回减免，未达返回 0")
    void fullReduction() {
        Coupon c = coupon(CouponService.TYPE_FULL_REDUCTION, 10000, 2000, 1.0, 0L, CouponService.SCOPE_ALL, null);
        assertEquals(2000, service.calculateDiscount(c, 15000));
        assertEquals(0, service.calculateDiscount(c, 5000));
    }

    @Test
    @DisplayName("无门槛：直接返回减免，且不超过商品金额")
    void noThreshold() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        assertEquals(500, service.calculateDiscount(c, 5000));
        assertEquals(300, service.calculateDiscount(c, 300)); // 封顶到商品金额
    }

    @Test
    @DisplayName("折扣：9折封顶30元")
    void discountWithCap() {
        Coupon c = coupon(CouponService.TYPE_DISCOUNT, 0, 0, 0.9, 3000L, CouponService.SCOPE_ALL, null);
        assertEquals(3000, service.calculateDiscount(c, 100000)); // 10000*0.1=10000 > 3000 封顶
        assertEquals(500, service.calculateDiscount(c, 5000));    // 5000*0.1=500 < 3000
    }

    // ---------- 领取规则 ----------

    @Test
    @DisplayName("领取成功：库存扣减 + 生成用户券")
    void claimSuccess() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        when(couponMapper.selectById(1L)).thenReturn(c);
        when(userCouponMapper.selectCount(any())).thenReturn(0L);
        when(couponMapper.update(isNull(), any())).thenReturn(1);
        UserCoupon uc = service.claim(2001L, 1L);
        assertEquals(CouponService.UC_UNUSED, uc.getStatus());
        verify(userCouponMapper).insert(any(UserCoupon.class));
    }

    @Test
    @DisplayName("领取失败：已抢光（库存为 0 时 update 返回 0）")
    void claimSoldOut() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        c.setClaimedCount(100); c.setTotalCount(100);
        when(couponMapper.selectById(1L)).thenReturn(c);
        when(userCouponMapper.selectCount(any())).thenReturn(0L);
        when(couponMapper.update(isNull(), any())).thenReturn(0);
        BizException ex = assertThrows(BizException.class, () -> service.claim(2001L, 1L));
        assertEquals(Code.COUPON_SOLD_OUT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("领取失败：每人限领（已领过）")
    void claimPerUserLimit() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        when(couponMapper.selectById(1L)).thenReturn(c);
        when(userCouponMapper.selectCount(any())).thenReturn(1L); // 已达限领
        BizException ex = assertThrows(BizException.class, () -> service.claim(2001L, 1L));
        assertTrue(ex.getMessage().contains("不可重复领取"));
    }

    @Test
    @DisplayName("领取失败：活动已暂停 / 不在领取时间")
    void claimInactiveOrTime() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        c.setStatus("PAUSED");
        when(couponMapper.selectById(1L)).thenReturn(c);
        assertThrows(BizException.class, () -> service.claim(2001L, 1L));

        Coupon c2 = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        c2.setStartAt(LocalDateTime.now().plusDays(1)); // 未开始
        when(couponMapper.selectById(2L)).thenReturn(c2);
        assertThrows(BizException.class, () -> service.claim(2001L, 2L));
    }

    // ---------- 核销 ----------

    @Test
    @DisplayName("核销成功：标记 USED 并返回抵扣")
    void redeemSuccess() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        UserCoupon uc = new UserCoupon();
        uc.setId(9L); uc.setUserId(2001L); uc.setCouponId(1L); uc.setStatus(CouponService.UC_UNUSED);
        uc.setExpireAt(LocalDateTime.now().plusDays(1));
        when(userCouponMapper.selectById(9L)).thenReturn(uc);
        when(couponMapper.selectById(1L)).thenReturn(c);
        when(itemMapper.selectById(9001L)).thenReturn(new Item());
        long discount = service.redeem(2001L, 9L, "NO123", 9001L, 5000);
        assertEquals(500, discount);
        ArgumentCaptor<UserCoupon> cap = ArgumentCaptor.forClass(UserCoupon.class);
        verify(userCouponMapper).updateById(cap.capture());
        assertEquals(CouponService.UC_USED, cap.getValue().getStatus());
        assertEquals("NO123", cap.getValue().getOrderNo());
    }

    @Test
    @DisplayName("核销失败：门槛未达")
    void redeemThresholdNotMet() {
        Coupon c = coupon(CouponService.TYPE_FULL_REDUCTION, 10000, 2000, 1.0, 0L, CouponService.SCOPE_ALL, null);
        UserCoupon uc = new UserCoupon();
        uc.setId(9L); uc.setUserId(2001L); uc.setCouponId(1L); uc.setStatus(CouponService.UC_UNUSED);
        uc.setExpireAt(LocalDateTime.now().plusDays(1));
        when(userCouponMapper.selectById(9L)).thenReturn(uc);
        when(couponMapper.selectById(1L)).thenReturn(c);
        when(itemMapper.selectById(9001L)).thenReturn(new Item());
        BizException ex = assertThrows(BizException.class, () -> service.redeem(2001L, 9L, "NO123", 9001L, 5000));
        assertTrue(ex.getMessage().contains("门槛"));
    }

    @Test
    @DisplayName("核销失败：已使用过")
    void redeemAlreadyUsed() {
        Coupon c = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, CouponService.SCOPE_ALL, null);
        UserCoupon uc = new UserCoupon();
        uc.setId(9L); uc.setUserId(2001L); uc.setCouponId(1L); uc.setStatus(CouponService.UC_USED);
        when(userCouponMapper.selectById(9L)).thenReturn(uc);
        assertThrows(BizException.class, () -> service.redeem(2001L, 9L, "NO123", 9001L, 5000));
    }

    @Test
    @DisplayName("核销失败：适用范围不匹配（数码9折券不能用于服饰）")
    void redeemScopeMismatch() {
        Coupon c = coupon(CouponService.TYPE_DISCOUNT, 0, 0, 0.9, 3000L, CouponService.SCOPE_CATEGORY, 100L);
        UserCoupon uc = new UserCoupon();
        uc.setId(9L); uc.setUserId(2001L); uc.setCouponId(1L); uc.setStatus(CouponService.UC_UNUSED);
        uc.setExpireAt(LocalDateTime.now().plusDays(1));
        Item item = new Item(); item.setCategoryId(200L); // 服饰类目
        when(userCouponMapper.selectById(9L)).thenReturn(uc);
        when(couponMapper.selectById(1L)).thenReturn(c);
        when(itemMapper.selectById(9001L)).thenReturn(item);
        assertThrows(BizException.class, () -> service.redeem(2001L, 9L, "NO123", 9001L, 5000));
    }

    // ---------- 释放 ----------

    @Test
    @DisplayName("订单取消/关单：释放已核销优惠券")
    void releaseByOrder() {
        UserCoupon uc = new UserCoupon();
        uc.setId(9L); uc.setUserId(2001L); uc.setCouponId(1L); uc.setStatus(CouponService.UC_USED);
        uc.setOrderNo("NO123");
        when(userCouponMapper.selectList(any())).thenReturn(java.util.List.of(uc));
        service.releaseByOrder("NO123");
        assertEquals(CouponService.UC_UNUSED, uc.getStatus());
        assertEquals(null, uc.getOrderNo());
        verify(userCouponMapper, times(1)).updateById(uc);
    }
}
