package com.idlefish.trade.member.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.member.entity.GrowthValueLog;
import com.idlefish.trade.member.entity.MemberLevel;
import com.idlefish.trade.member.entity.UserGrowth;
import com.idlefish.trade.member.mapper.GrowthValueLogMapper;
import com.idlefish.trade.member.mapper.MemberLevelMapper;
import com.idlefish.trade.member.mapper.UserGrowthMapper;
import com.idlefish.trade.user.mapper.UserMapper;
import com.idlefish.trade.member.vo.GrowthAccountAdminVO;
import com.idlefish.trade.member.vo.GrowthAccountVO;
import com.idlefish.trade.member.vo.GrowthValueLogVO;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.risk.mapper.DeviceFingerprintMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * ITER-F07 成长值服务离线单测（Mockito，无 Spring / 无 MySQL）。
 * 覆盖多维累计、冻结 fail-closed、幂等、账户视图、后台列表、流水过滤与衰减写入。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrowthValueServiceTest {

    @Mock private UserGrowthMapper userGrowthMapper;
    @Mock private UserMapper userMapper;
    @Mock private GrowthValueLogMapper growthValueLogMapper;
    @Mock private MemberLevelMapper memberLevelMapper;
    @Mock private DeviceFingerprintMapper deviceFingerprintMapper;
    @Mock private NotificationService notificationService;
    @Mock private MetricsRegistry metrics;
    @Mock private IdlefishProperties idlefishProperties;

    private IdlefishProperties.Growth growth;
    private GrowthValueService service;

    private static final Long USER = 1L;

    @BeforeAll
    static void initMpMetadata() {
        // 纯 Mockito 环境无 MyBatis 上下文：手工初始化 MP lambda 列缓存，
        // 否则 LambdaUpdateWrapper.set(UserGrowth::getXxx) 抛 "can not find lambda cache"
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "dummy");
        TableInfoHelper.initTableInfo(assistant, UserGrowth.class);
        TableInfoHelper.initTableInfo(assistant, GrowthValueLog.class);
        TableInfoHelper.initTableInfo(assistant, MemberLevel.class);
    }

    @BeforeEach
    void setUp() {
        growth = new IdlefishProperties.Growth();
        when(idlefishProperties.getGrowth()).thenReturn(growth);
        service = new GrowthValueService(userGrowthMapper, userMapper, growthValueLogMapper, memberLevelMapper,
                deviceFingerprintMapper, notificationService, metrics, idlefishProperties);
    }

    private static UserGrowth account(int growthValue, int frozen) {
        UserGrowth g = new UserGrowth();
        g.setId(10L);
        g.setUserId(USER);
        g.setGrowthValue(growthValue);
        g.setTotalEarned(growthValue);
        g.setLevelCode("L2");
        g.setGrowthFrozen(frozen);
        g.setLastActiveAt(LocalDateTime.now().minusDays(10));
        return g;
    }

    private void stubTiers() {
        MemberLevel l1 = new MemberLevel();
        l1.setLevelCode("L1");
        l1.setLevelName("注册会员");
        l1.setMinGrowth(0);
        MemberLevel l2 = new MemberLevel();
        l2.setLevelCode("L2");
        l2.setLevelName("青铜会员");
        l2.setMinGrowth(100);
        l2.setFreeShipping(1);
        when(memberLevelMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(l1, l2)));
    }

    private void stubNotLogged() {
        when(growthValueLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
    }

    // ===== 多维累计 =====

    @Test
    void earnTrade_accruesOnHappyPath() {
        UserGrowth g = account(0, 0);
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(g);
        stubTiers();
        stubNotLogged();
        when(userGrowthMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(growthValueLogMapper.insert(any(GrowthValueLog.class))).thenReturn(1);

        int earned = service.earnTrade(USER, "ORD1", 12345L); // 123.45 元 → 123 点

        assertEquals(123, earned);
        verify(growthValueLogMapper, times(1)).insert(any(GrowthValueLog.class));
        verify(userGrowthMapper, times(1)).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void earnTrade_frozenIsFailClosedWithZeroDeltaLog() {
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(500, 1));
        stubNotLogged();
        when(growthValueLogMapper.insert(any(GrowthValueLog.class))).thenReturn(1);

        int earned = service.earnTrade(USER, "ORD1", 10000L);

        assertEquals(0, earned);
        verify(userGrowthMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        verify(growthValueLogMapper, times(1)).insert(any(GrowthValueLog.class)); // 0 增量流水留痕
    }

    @Test
    void earnTrade_idempotentWhenAlreadyLogged() {
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(0, 0));
        when(growthValueLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        int earned = service.earnTrade(USER, "ORD1", 10000L);

        assertEquals(0, earned);
        verify(userGrowthMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        verify(growthValueLogMapper, never()).insert(any(GrowthValueLog.class));
    }

    @Test
    void earnTrade_disabledReturnsZero() {
        growth.setEnabled(false);
        assertEquals(0, service.earnTrade(USER, "ORD1", 10000L));
        verify(userGrowthMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void earnPublish_fixed() {
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(0, 0));
        stubTiers();
        stubNotLogged();
        when(userGrowthMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(growthValueLogMapper.insert(any(GrowthValueLog.class))).thenReturn(1);

        assertEquals(growth.getPublishFixed(), service.earnPublish(USER, 99L));
    }

    // ===== 判责扣减 / 回补 =====

    @Test
    void deductByDispute_cappedAndLogged() {
        UserGrowth g = account(1000, 0);
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(g);
        stubTiers();
        stubNotLogged();
        when(userGrowthMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(growthValueLogMapper.insert(any(GrowthValueLog.class))).thenReturn(1);

        int actual = service.deductByDispute(USER, "DP1", 500, "卖家责任");

        assertEquals(500, actual); // cap 默认 500
        verify(growthValueLogMapper, times(1)).insert(any(GrowthValueLog.class));
    }

    @Test
    void deductByDispute_defaultAmountUsesConfigCap() {
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(1000, 0));
        stubTiers();
        stubNotLogged();
        when(userGrowthMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(growthValueLogMapper.insert(any(GrowthValueLog.class))).thenReturn(1);

        assertEquals(500, service.deductByDispute(USER, "DP2", "卖家责任"));
    }

    @Test
    void restoreByDispute_earnsBack() {
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(500, 0));
        stubTiers();
        stubNotLogged();
        when(userGrowthMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(growthValueLogMapper.insert(any(GrowthValueLog.class))).thenReturn(1);

        assertEquals(200, service.restoreByDispute(USER, "DP1", 200, "申诉成功"));
    }

    // ===== 账户视图 / 列表 =====

    @Test
    void accountView_containsDecayInfo() {
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(300, 0));
        stubTiers();

        GrowthAccountVO v = service.accountView(USER);

        assertNotNull(v);
        assertEquals("L2", v.getLevelCode());       // 用户端优先展示已生效等级
        assertEquals(300, v.getGrowthValue());
        assertEquals("青铜会员", v.getLevelName());
        assertFalse(v.getFrozen());
        assertEquals(1, v.getFreeShipping());
        assertTrue(v.getDaysUntilDecay() > 0);       // 10 天未活跃，距 90 天阈值还早
        assertEquals(growth.getDecayNotifyDays(), v.getDecayNotifyDays());
        assertEquals("v1", v.getRuleVersion());
    }

    @Test
    void accountViewReadonly_absentAccountReturnsZeroView() {
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        GrowthAccountVO v = service.accountViewReadonly(USER);

        assertNotNull(v);
        assertEquals(0, v.getGrowthValue());
        assertFalse(v.getFrozen());
        verify(userGrowthMapper, never()).insert(any(UserGrowth.class)); // 只读不创建
    }

    @Test
    void listAccounts_frozenFilter() {
        Page<UserGrowth> p = new Page<>(1, 20);
        p.setRecords(List.of(account(300, 1)));
        p.setTotal(1);
        when(userGrowthMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(p);
        stubTiers();

        IPage<GrowthAccountAdminVO> r = service.listAccounts(1, 20, true);

        assertEquals(1, r.getTotal());
        assertTrue(r.getRecords().get(0).getFrozen());
        assertEquals("青铜会员", r.getRecords().get(0).getLevelName());
        assertNotNull(r.getRecords().get(0).getLastActiveAt());
    }

    // ===== 流水过滤 =====

    @Test
    void logs_filtersByBizType() {
        Page<GrowthValueLog> p = new Page<>(1, 20);
        p.setRecords(List.of());
        p.setTotal(0);
        when(growthValueLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(p);

        IPage<GrowthValueLogVO> r = service.logs(USER, "EARN_TRADE", 1, 20);

        assertEquals(0, r.getTotal());
        verify(growthValueLogMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    // ===== 衰减写入 / 通知去重 =====

    @Test
    void applyDecay_writesNegativeLogAndDoesNotRefreshActivity() {
        UserGrowth g = account(300, 0);
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(g);
        stubTiers();
        stubNotLogged();
        when(userGrowthMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(growthValueLogMapper.insert(any(GrowthValueLog.class))).thenReturn(1);

        boolean ok = service.applyDecay(USER, 300, 250, "2026-10-23", "不活跃衰减");

        assertTrue(ok);
        verify(growthValueLogMapper, times(1)).insert(any(GrowthValueLog.class));
        // CAS 只改成长值，不刷新 lastActiveAt（衰减不是活跃行为）
        verify(userGrowthMapper, times(1)).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void applyDecay_idempotentSameDay() {
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(300, 0));
        when(growthValueLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertFalse(service.applyDecay(USER, 300, 250, "2026-10-23", "不活跃衰减"));
        verify(userGrowthMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void applyDecay_rejectsNonDecreasingTarget() {
        assertFalse(service.applyDecay(USER, 300, 300, "2026-10-23", "无变化"));
        assertFalse(service.applyDecay(USER, 300, 400, "2026-10-23", "异常增长"));
    }

    @Test
    void markDecayNotified_updatesTimestamp() {
        when(userGrowthMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        assertTrue(service.markDecayNotified(USER));
    }

    // ===== 冻结 / 解冻 =====

    @Test
    void freezeAndUnfreeze() {
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(300, 0));
        when(userGrowthMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        assertTrue(service.freeze(USER, "疑似刷分"));

        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(300, 1));
        assertTrue(service.unfreeze(USER));
    }

    @Test
    void inactiveDaysOf_fallsBackToCreatedAt() {
        UserGrowth g = new UserGrowth();
        g.setUserId(USER);
        g.setCreatedAt(LocalDateTime.now().minusDays(100));
        assertTrue(service.inactiveDaysOf(g) >= 99);
        assertEquals(0, service.inactiveDaysOf(null));
    }

    @Test
    void touchActive_refreshesTimestamp() {
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(300, 0));
        when(userGrowthMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        assertTrue(service.touchActive(USER));
    }

    // ===== 冲正 =====

    @Test
    void reverseByOrder_deductsEarnedTradeLogs() {
        GrowthValueLog earned = new GrowthValueLog();
        earned.setUserId(USER);
        earned.setBizType(GrowthValueService.BIZ_EARN_TRADE);
        earned.setBizId("ORD1");
        earned.setDelta(100);
        when(growthValueLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(earned));
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(500, 0));
        stubTiers();
        stubNotLogged();
        when(userGrowthMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(growthValueLogMapper.insert(any(GrowthValueLog.class))).thenReturn(1);

        service.reverseByOrder("ORD1");

        verify(growthValueLogMapper, times(1)).insert(any(GrowthValueLog.class)); // 一条 DEDUCT_REVERSE 负向流水
        verify(userGrowthMapper, times(1)).update(any(), any(LambdaUpdateWrapper.class)); // CAS 扣回 100 点
    }

    // ===== 存量用户初始化（PRD §12.4 遗留项②）=====

    @Test
    void initMissingUsers_createsDefaultAccounts() {
        when(userMapper.selectMissingGrowthUserIds(anyInt())).thenReturn(List.of(2L, 3L, 4L));
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null); // ensure 触发插入
        when(userGrowthMapper.insert(any(UserGrowth.class))).thenReturn(1);

        int count = service.initMissingUsers(1000);

        assertEquals(3, count);
        verify(userGrowthMapper, times(3)).insert(any(UserGrowth.class));
    }

    @Test
    void initMissingUsers_returnsZeroWhenNoneMissing() {
        when(userMapper.selectMissingGrowthUserIds(anyInt())).thenReturn(java.util.Collections.emptyList());

        assertEquals(0, service.initMissingUsers(1000));
        verify(userGrowthMapper, never()).insert(any(UserGrowth.class));
    }

    @Test
    void initMissingUsers_disabledSkipsQuery() {
        growth.setEnabled(false);

        assertEquals(0, service.initMissingUsers(1000));
        verify(userMapper, never()).selectMissingGrowthUserIds(anyInt());
        verify(userGrowthMapper, never()).insert(any(UserGrowth.class));
    }

    // ===== 申诉成功回补（REQ-10 便捷重载）=====

    @Test
    void restoreByDispute_autoDerivesPenaltyAndRestores() {
        when(growthValueLogMapper.sumAbsDeltaByBiz(eq(USER), eq(GrowthValueService.BIZ_DEDUCT_DISPUTE), anyString()))
                .thenReturn(200L);
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(500, 0));
        stubTiers();
        stubNotLogged();
        when(userGrowthMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(growthValueLogMapper.insert(any(GrowthValueLog.class))).thenReturn(1);

        assertEquals(200, service.restoreByDispute(USER, "DP9"));
        verify(growthValueLogMapper, times(1))
                .sumAbsDeltaByBiz(eq(USER), eq(GrowthValueService.BIZ_DEDUCT_DISPUTE), anyString());
        verify(growthValueLogMapper, times(1)).insert(any(GrowthValueLog.class)); // 一条 RESTORE_DISPUTE 正流水
    }

    @Test
    void restoreByDispute_noPenaltyReturnsZero() {
        when(growthValueLogMapper.sumAbsDeltaByBiz(anyLong(), anyString(), anyString())).thenReturn(0L);

        assertEquals(0, service.restoreByDispute(USER, "DP9"));
        verify(userGrowthMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        verify(growthValueLogMapper, never()).insert(any(GrowthValueLog.class));
    }
}
