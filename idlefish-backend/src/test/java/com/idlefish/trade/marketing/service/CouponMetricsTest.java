package com.idlefish.trade.marketing.service;

import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.marketing.entity.Coupon;
import com.idlefish.trade.marketing.entity.UserCoupon;
import com.idlefish.trade.marketing.mapper.CouponMapper;
import com.idlefish.trade.marketing.mapper.UserCouponMapper;
import com.idlefish.trade.notify.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 埋点单测（F-12.2）：验证优惠券领取/核销成功后确实触发 MetricsRegistry 计数。
 * 纯 Mockito，无 Spring / DB，离线可跑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponMetricsTest {

    @Mock private CouponMapper couponMapper;
    @Mock private UserCouponMapper userCouponMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private NotificationService notificationService;
    @Mock private MetricsRegistry metrics;
    @InjectMocks private CouponService service;

    @Test
    void claim_success_records_metric() {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setName("测试券");
        c.setStatus(CouponService.STATUS_ACTIVE);
        c.setStartAt(LocalDateTime.now().minusDays(1));
        c.setEndAt(LocalDateTime.now().plusDays(1));
        c.setTotalCount(100);
        c.setPerUserLimit(1);
        c.setScope(CouponService.SCOPE_ALL);
        when(couponMapper.selectById(1L)).thenReturn(c);
        when(userCouponMapper.selectCount(ArgumentMatchers.any())).thenReturn(0L);
        when(couponMapper.update(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(1);
        when(userCouponMapper.insert(ArgumentMatchers.any(UserCoupon.class))).thenReturn(1);
        doNothing().when(notificationService).notify(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());

        service.claim(1L, 1L);

        verify(metrics).increment("coupon.claim.success");
    }

    @Test
    void redeem_success_records_metric() {
        UserCoupon uc = new UserCoupon();
        uc.setId(9L);
        uc.setUserId(1L);
        uc.setCouponId(1L);
        uc.setStatus(CouponService.UC_UNUSED);
        when(userCouponMapper.selectById(9L)).thenReturn(uc);

        Coupon c = new Coupon();
        c.setId(1L);
        c.setStatus(CouponService.STATUS_ACTIVE);
        c.setType(CouponService.TYPE_NO_THRESHOLD);
        c.setReduceAmount(500L);
        c.setScope(CouponService.SCOPE_ALL);
        when(couponMapper.selectById(1L)).thenReturn(c);

        Item item = new Item();
        item.setId(10L);
        when(itemMapper.selectById(10L)).thenReturn(item);
        when(userCouponMapper.updateById(ArgumentMatchers.any(UserCoupon.class))).thenReturn(1);

        service.redeem(1L, 9L, "ON1", 10L, 10000);

        verify(metrics).increment("coupon.redeem.success");
    }
}
