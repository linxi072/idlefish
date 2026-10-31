package com.idlefish.trade.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.marketing.entity.Coupon;
import com.idlefish.trade.marketing.entity.UserCoupon;
import com.idlefish.trade.marketing.mapper.CouponMapper;
import com.idlefish.trade.marketing.mapper.UserCouponMapper;
import com.idlefish.trade.marketing.vo.CouponVO;
import com.idlefish.trade.marketing.vo.UserCouponVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CouponStore 数据访问单测（F-10 续拆）：领券中心 / 我的券 / 可用券 / 释放 / 批量加载。
 * 全程 Mock Mapper，无 Spring、无 DB，离线可跑（mvn -o -P!micrometer test）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponStoreTest {

    @Mock private CouponMapper couponMapper;
    @Mock private UserCouponMapper userCouponMapper;
    @Mock private ItemMapper itemMapper;
    @InjectMocks private CouponStore store;

    private Coupon coupon(String type, long threshold, long reduce, Double rate, Long cap, Long id) {
        Coupon c = new Coupon();
        c.setId(id);
        c.setName("券" + id);
        c.setType(type);
        c.setThresholdAmount(threshold);
        c.setReduceAmount(reduce);
        c.setDiscountRate(rate);
        c.setMaxDiscountAmount(cap);
        c.setScope(CouponService.SCOPE_ALL);
        c.setStatus(CouponService.STATUS_ACTIVE);
        c.setStartAt(LocalDateTime.now().minusDays(1));
        c.setEndAt(LocalDateTime.now().plusDays(1));
        c.setTotalCount(100);
        c.setClaimedCount(0);
        c.setPerUserLimit(1);
        return c;
    }

    // ---------- 领券中心分页 ----------

    @Test
    @DisplayName("pageCenter：无 userId 时 claimedIds 为空")
    void pageCenterNoUser() {
        Coupon c1 = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, 1L);
        Coupon c2 = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, 2L);
        IPage<Coupon> page = new Page<>(1, 10, 2);
        page.setRecords(List.of(c1, c2));
        when(couponMapper.selectPage(any(), any())).thenReturn(page);

        IPage<CouponVO> result = store.pageCenter(1, 10, null);
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertFalse(result.getRecords().get(0).getClaimed());
        verify(userCouponMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("pageCenter：有 userId 时回填已领集合")
    void pageCenterWithUser() {
        Coupon c1 = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, 1L);
        Coupon c2 = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, 2L);
        IPage<Coupon> page = new Page<>(1, 10, 2);
        page.setRecords(List.of(c1, c2));
        when(couponMapper.selectPage(any(), any())).thenReturn(page);
        UserCoupon owned = new UserCoupon();
        owned.setCouponId(1L);
        when(userCouponMapper.selectList(any())).thenReturn(List.of(owned));

        IPage<CouponVO> result = store.pageCenter(1, 10, 2001L);
        assertEquals(2, result.getRecords().size());
        assertTrue(result.getRecords().get(0).getClaimed());
        assertFalse(result.getRecords().get(1).getClaimed());
    }

    // ---------- 我的优惠券 ----------

    @Test
    @DisplayName("myCoupons：批量加载券模板并映射 VO")
    void myCoupons() {
        UserCoupon uc1 = new UserCoupon();
        uc1.setId(11L); uc1.setCouponId(1L); uc1.setUserId(2001L);
        UserCoupon uc2 = new UserCoupon();
        uc2.setId(12L); uc2.setCouponId(2L); uc2.setUserId(2001L);
        when(userCouponMapper.selectList(any())).thenReturn(List.of(uc1, uc2));
        Coupon c1 = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, 1L);
        Coupon c2 = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, 2L);
        when(couponMapper.selectBatchIds(any())).thenReturn(List.of(c1, c2));

        List<UserCouponVO> vos = store.myCoupons(2001L, null);
        assertEquals(2, vos.size());
        assertEquals("券1", vos.get(0).getName());
        assertEquals("券2", vos.get(1).getName());
    }

    // ---------- 下单可用券 ----------

    @Test
    @DisplayName("availableCoupons：跳过过期券，按抵扣金额降序")
    void availableCoupons() {
        UserCoupon uc1 = new UserCoupon();
        uc1.setId(11L); uc1.setCouponId(1L); uc1.setUserId(2001L);
        uc1.setStatus(CouponService.UC_UNUSED); uc1.setExpireAt(LocalDateTime.now().plusDays(1));
        UserCoupon uc2 = new UserCoupon();
        uc2.setId(12L); uc2.setCouponId(2L); uc2.setUserId(2001L);
        uc2.setStatus(CouponService.UC_UNUSED); uc2.setExpireAt(LocalDateTime.now().plusDays(1));
        UserCoupon uc3 = new UserCoupon();
        uc3.setId(13L); uc3.setCouponId(3L); uc3.setUserId(2001L);
        uc3.setStatus(CouponService.UC_UNUSED); uc3.setExpireAt(LocalDateTime.now().minusDays(1)); // 过期跳过
        when(userCouponMapper.selectList(any())).thenReturn(List.of(uc1, uc2, uc3));
        when(itemMapper.selectById(any())).thenReturn(new Item());
        Coupon c1 = coupon(CouponService.TYPE_FULL_REDUCTION, 10000, 2000, 1.0, 0L, 1L);
        Coupon c2 = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, 2L);
        Coupon c3 = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 999, 1.0, 0L, 3L);
        when(couponMapper.selectBatchIds(any())).thenReturn(List.of(c1, c2, c3));

        List<UserCouponVO> vos = store.availableCoupons(2001L, 9001L, 15000);
        assertEquals(2, vos.size());
        assertEquals(2000L, vos.get(0).getDiscountAmount());
        assertEquals(500L, vos.get(1).getDiscountAmount());
    }

    // ---------- 释放 ----------

    @Test
    @DisplayName("releaseByOrder：空/blank 订单号直接返回，不触达 Mapper")
    void releaseByOrderBlank() {
        store.releaseByOrder("");
        store.releaseByOrder(null);
        verifyNoInteractions(userCouponMapper);
    }

    @Test
    @DisplayName("releaseByOrder：USED → UNUSED 解绑订单")
    void releaseByOrderValid() {
        UserCoupon uc = new UserCoupon();
        uc.setId(9L); uc.setUserId(2001L); uc.setCouponId(1L);
        uc.setStatus(CouponService.UC_USED); uc.setOrderNo("NO123");
        when(userCouponMapper.selectList(any())).thenReturn(List.of(uc));

        store.releaseByOrder("NO123");
        assertEquals(CouponService.UC_UNUSED, uc.getStatus());
        assertNull(uc.getOrderNo());
        verify(userCouponMapper).updateById(uc);
    }

    // ---------- 批量加载 ----------

    @Test
    @DisplayName("batchCoupons：空集合返回空 Map")
    void batchCouponsEmpty() {
        assertTrue(store.batchCoupons(Collections.emptySet()).isEmpty());
    }

    @Test
    @DisplayName("batchCoupons：一次 IN 查询映射为 Map")
    void batchCouponsMapped() {
        Coupon c1 = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, 1L);
        Coupon c2 = coupon(CouponService.TYPE_NO_THRESHOLD, 0, 500, 1.0, 0L, 2L);
        when(couponMapper.selectBatchIds(any())).thenReturn(List.of(c1, c2));

        Map<Long, Coupon> map = store.batchCoupons(Set.of(1L, 2L));
        assertEquals(2, map.size());
        assertEquals(1L, map.get(1L).getId());
        assertEquals(2L, map.get(2L).getId());
    }
}
