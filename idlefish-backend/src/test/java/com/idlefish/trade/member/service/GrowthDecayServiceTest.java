package com.idlefish.trade.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.member.entity.UserGrowth;
import com.idlefish.trade.member.mapper.UserGrowthMapper;
import com.idlefish.trade.member.service.GrowthDecayService.DecaySummary;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ITER-F07 衰减调度服务离线单测（Mockito，无 Spring / 无 MySQL）。
 * 覆盖衰减扫描（纯函数精算 + 幂等 + 降级统计）与提前预警（窗口 + 当日去重）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrowthDecayServiceTest {

    @Mock private UserGrowthMapper userGrowthMapper;
    @Mock private GrowthValueService growthValueService;
    @Mock private NotificationService notificationService;
    @Mock private MetricsRegistry metrics;
    @Mock private IdlefishProperties idlefishProperties;

    private IdlefishProperties.Growth growth;
    private IdlefishProperties.Notify notifyCfg;
    private GrowthDecayService service;

    private static final Long USER = 7L;

    @BeforeEach
    void setUp() {
        growth = new IdlefishProperties.Growth();
        notifyCfg = new IdlefishProperties.Notify();
        when(idlefishProperties.getGrowth()).thenReturn(growth);
        when(idlefishProperties.getNotify()).thenReturn(notifyCfg);
        service = new GrowthDecayService(userGrowthMapper, growthValueService, notificationService,
                metrics, idlefishProperties);
    }

    private static UserGrowth dueUser(long id, int growthValue, String levelCode,
                                      LocalDateTime lastActiveAt) {
        UserGrowth g = new UserGrowth();
        g.setId(id);
        g.setUserId(USER);
        g.setGrowthValue(growthValue);
        g.setLevelCode(levelCode);
        g.setLastActiveAt(lastActiveAt);
        return g;
    }

    // ===== 衰减扫描 =====

    @Test
    void scanAndDecay_disabledReturnsEmpty() {
        growth.setDecayEnabled(false);
        DecaySummary s = service.scanAndDecay();
        assertEquals(0, s.scanned());
        verify(userGrowthMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void scanAndDecay_appliesDecayAndCountsDowngrade() {
        UserGrowth g = dueUser(1L, 300, "L3", LocalDateTime.now().minusDays(100));
        when(userGrowthMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(g));
        when(growthValueService.inactiveDaysOf(any())).thenReturn(100L);
        when(growthValueService.applyDecay(eq(USER), eq(300), eq(250), anyString(), anyString()))
                .thenReturn(true);
        // 衰减后等级由 L3 降为 L1（levelOf 重新查询）
        UserGrowth after = dueUser(1L, 250, "L1", LocalDateTime.now().minusDays(100));
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(after);

        DecaySummary s = service.scanAndDecay();

        assertEquals(1, s.scanned());
        assertEquals(1, s.decayed());
        assertEquals(1, s.downgraded());
        verify(growthValueService, times(1))
                .applyDecay(eq(USER), eq(300), eq(250), eq(LocalDate.now().toString()), anyString());
    }

    @Test
    void scanAndDecay_notDueUserSkipped() {
        // 未活跃 80 天 < 90 阈值：decayDue=false，不衰减
        UserGrowth g = dueUser(1L, 300, "L2", LocalDateTime.now().minusDays(80));
        when(userGrowthMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(g));
        when(growthValueService.inactiveDaysOf(any())).thenReturn(80L);

        DecaySummary s = service.scanAndDecay();

        assertEquals(1, s.scanned());
        assertEquals(0, s.decayed());
        verify(growthValueService, never()).applyDecay(anyLong(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void scanAndDecay_flooredAtZeroNoDowngradeSpurious() {
        // 成长值已为 0：衰减目标 = 现值，after >= before 跳过
        UserGrowth g = dueUser(1L, 0, "L1", LocalDateTime.now().minusDays(200));
        when(userGrowthMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(g));
        when(growthValueService.inactiveDaysOf(any())).thenReturn(200L);

        DecaySummary s = service.scanAndDecay();

        assertEquals(1, s.scanned());
        assertEquals(0, s.decayed());
        verify(growthValueService, never()).applyDecay(anyLong(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void scanAndDecay_applyDecayRejectedIsNotCounted() {
        UserGrowth g = dueUser(1L, 300, "L3", LocalDateTime.now().minusDays(100));
        when(userGrowthMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(g));
        when(growthValueService.inactiveDaysOf(any())).thenReturn(100L);
        when(growthValueService.applyDecay(anyLong(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(false); // 已衰减过 / CAS 失败

        DecaySummary s = service.scanAndDecay();

        assertEquals(1, s.scanned());
        assertEquals(0, s.decayed());
        assertEquals(0, s.downgraded());
    }

    @Test
    void scanAndDecay_stopsOnPartialBatch() {
        // 首批不满 500 → 扫描即止（末批语义），验证批次推进与终止
        UserGrowth g1 = dueUser(1L, 300, "L2", LocalDateTime.now().minusDays(100));
        UserGrowth g2 = dueUser(2L, 500, "L3", LocalDateTime.now().minusDays(120));
        AtomicInteger call = new AtomicInteger();
        when(userGrowthMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            if (call.getAndIncrement() == 0) {
                return List.of(g1, g2);
            }
            return List.of();
        });
        when(growthValueService.inactiveDaysOf(any())).thenAnswer(inv -> {
            long id = ((UserGrowth) inv.getArgument(0)).getId();
            return id == 1L ? 100L : 120L;
        });
        when(growthValueService.applyDecay(anyLong(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(true);
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(dueUser(1L, 250, "L1", null));

        DecaySummary s = service.scanAndDecay();

        assertEquals(2, s.scanned());
        assertEquals(2, s.decayed());
        verify(userGrowthMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void scanAndDecay_singleUserFailureDoesNotAbortBatch() {
        UserGrowth bad = dueUser(1L, 300, "L2", null);
        UserGrowth good = dueUser(2L, 500, "L3", LocalDateTime.now().minusDays(100));
        when(userGrowthMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(bad, good));
        when(growthValueService.inactiveDaysOf(any())).thenAnswer(inv -> {
            long id = ((UserGrowth) inv.getArgument(0)).getId();
            if (id == 1L) {
                throw new RuntimeException("db blip");
            }
            return 100L;
        });
        when(growthValueService.applyDecay(anyLong(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(true);
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(dueUser(2L, 0, "L3", null));

        DecaySummary s = service.scanAndDecay();

        assertEquals(2, s.scanned());
        assertEquals(1, s.decayed()); // 坏用户被跳过，好用户照常
    }

    // ===== 提前预警 =====

    @Test
    void notifyUpcomingDecay_withinWindowNotifiesOnce() {
        UserGrowth g = dueUser(1L, 300, "L2", LocalDateTime.now().minusDays(85));
        when(userGrowthMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(g));
        when(growthValueService.inactiveDaysOf(any())).thenReturn(85L);
        when(growthValueService.markDecayNotified(USER)).thenReturn(true);

        DecaySummary s = service.notifyUpcomingDecay();

        assertEquals(1, s.scanned());
        assertEquals(1, s.notified());
        verify(notificationService, times(1)).notify(eq(USER), eq(NotificationType.GROWTH_DECAY_WARNING),
                anyString(), anyString(), anyString());
        verify(growthValueService, times(1)).markDecayNotified(USER);
    }

    @Test
    void notifyUpcomingDecay_notifiedTodayIsDeduped() {
        UserGrowth g = dueUser(1L, 300, "L2", LocalDateTime.now().minusDays(85));
        g.setDecayNotifiedAt(LocalDateTime.now());
        when(userGrowthMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(g));
        when(growthValueService.inactiveDaysOf(any())).thenReturn(85L);

        DecaySummary s = service.notifyUpcomingDecay();

        assertEquals(1, s.scanned());
        assertEquals(0, s.notified());
        verify(growthValueService, never()).markDecayNotified(anyLong());
    }

    @Test
    void notifyUpcomingDecay_outsideWindowSkipped() {
        // 还剩 20 天才衰减（> 7 日预警窗）：不通知
        UserGrowth g = dueUser(1L, 300, "L2", LocalDateTime.now().minusDays(70));
        when(userGrowthMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(g));
        when(growthValueService.inactiveDaysOf(any())).thenReturn(70L);

        DecaySummary s = service.notifyUpcomingDecay();

        assertEquals(1, s.scanned());
        assertEquals(0, s.notified());
    }

    @Test
    void notifyUpcomingDecay_disabledReturnsEmpty() {
        growth.setDecayEnabled(false);
        DecaySummary s = service.notifyUpcomingDecay();
        assertEquals(0, s.scanned());
        verify(userGrowthMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    // ===== 运营告警 =====

    @Test
    void adminSummaryGoesToConfiguredAdmin() {
        growth.setAlertAdminUserId(42L);
        when(userGrowthMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.scanAndDecay();

        verify(notificationService, times(1)).notify(eq(42L), eq(NotificationType.SYSTEM_ALERT),
                anyString(), anyString(), anyString());
    }

    @Test
    void adminSummaryFallsBackToNotifyAdmin() {
        notifyCfg.setAlertAdminUserId(1L);
        when(userGrowthMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.notifyUpcomingDecay();

        verify(notificationService, times(1)).notify(eq(1L), eq(NotificationType.SYSTEM_ALERT),
                anyString(), anyString(), anyString());
    }

    @Test
    void decaySummaryRecordAccessors() {
        DecaySummary s = new DecaySummary(3, 2, 1, 4);
        assertEquals(3, s.scanned());
        assertEquals(2, s.decayed());
        assertEquals(1, s.downgraded());
        assertEquals(4, s.notified());
        assertFalse(DecaySummary.empty().scanned() > 0);
        assertTrue(DecaySummary.empty().decayed() == 0);
    }
}
