package com.idlefish.trade.analytics.service;

import com.idlefish.trade.analytics.mapper.AnalyticsMapper;
import com.idlefish.trade.analytics.vo.AnalyticsMemberGrowth;
import com.idlefish.trade.member.entity.UserGrowth;
import com.idlefish.trade.member.mapper.GrowthValueLogMapper;
import com.idlefish.trade.member.mapper.UserGrowthMapper;
import com.idlefish.trade.member.service.GrowthValueService;
import com.idlefish.trade.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * ITER-F07 遗留项④ 会员成长值对账报表离线单测（Mockito，无 Spring / 无 MySQL）。
 * 覆盖三方聚合口径与对账差额、以及映射异常降级为 0 的 best-effort 兜底。
 */
@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

    @Mock private AnalyticsMapper analyticsMapper;
    @Mock private UserMapper userMapper;
    @Mock private UserGrowthMapper userGrowthMapper;
    @Mock private GrowthValueLogMapper growthValueLogMapper;

    private AdminAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AdminAnalyticsService(analyticsMapper, userMapper, userGrowthMapper, growthValueLogMapper);
    }

    @Test
    void memberGrowth_aggregatesAndReconciles() {
        when(userMapper.selectCount(any())).thenReturn(100L);
        // 同一方法两次调用：null 实参=账户总数，Wrapper 实参=冻结数
        when(userGrowthMapper.selectCount(any())).thenAnswer(inv ->
                inv.getArgument(0) == null ? 80L : 3L);
        when(userGrowthMapper.sumGrowthValue()).thenReturn(5000L);
        when(growthValueLogMapper.sumTotalDelta()).thenReturn(5000L); // 与存量总成长值一致
        when(growthValueLogMapper.sumPositiveDelta()).thenReturn(8000L);
        when(growthValueLogMapper.sumAbsByBizType(GrowthValueService.BIZ_DECAY)).thenReturn(1000L);
        when(growthValueLogMapper.sumAbsByBizType(GrowthValueService.BIZ_DEDUCT_DISPUTE)).thenReturn(500L);
        when(growthValueLogMapper.sumAbsByBizType(GrowthValueService.BIZ_DEDUCT_REVERSE)).thenReturn(200L);
        when(growthValueLogMapper.sumAbsByBizType(GrowthValueService.BIZ_RESTORE_DISPUTE)).thenReturn(300L);

        AnalyticsMemberGrowth r = service.memberGrowth();

        assertEquals(100L, r.getTotalUsers());
        assertEquals(80L, r.getUsersWithAccount());
        assertEquals(20L, r.getUsersWithoutAccount());
        assertEquals(5000L, r.getTotalGrowthValue());
        assertEquals(8000L, r.getCumulativeEarned());
        assertEquals(1000L, r.getCumulativeDecay());
        assertEquals(500L, r.getCumulativeDeductDispute());
        assertEquals(200L, r.getCumulativeDeductReverse());
        assertEquals(300L, r.getRestored());
        assertEquals(3L, r.getFrozenAccounts());
        assertEquals(5000L, r.getLedgerSum());
        assertEquals(0L, r.getReconciliationDiff()); // 存量总成长值 == 流水总账
    }

    @Test
    void memberGrowth_handlesMapperFailureGracefully() {
        // 模拟成长值相关表缺失 / 查询异常：best-effort 降级为 0，不抛异常
        when(userMapper.selectCount(any())).thenReturn(50L);
        when(userGrowthMapper.selectCount(any())).thenThrow(new RuntimeException("table missing"));
        when(userGrowthMapper.sumGrowthValue()).thenThrow(new RuntimeException("table missing"));
        when(growthValueLogMapper.sumTotalDelta()).thenThrow(new RuntimeException("table missing"));
        when(growthValueLogMapper.sumPositiveDelta()).thenThrow(new RuntimeException("table missing"));
        when(growthValueLogMapper.sumAbsByBizType(any())).thenThrow(new RuntimeException("table missing"));

        AnalyticsMemberGrowth r = service.memberGrowth();

        assertEquals(50L, r.getTotalUsers());
        assertEquals(0L, r.getUsersWithAccount());
        assertEquals(50L, r.getUsersWithoutAccount());
        assertEquals(0L, r.getTotalGrowthValue());
        assertEquals(0L, r.getLedgerSum());
        assertEquals(0L, r.getReconciliationDiff());
    }
}
