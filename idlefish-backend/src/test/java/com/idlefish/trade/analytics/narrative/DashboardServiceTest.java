package com.idlefish.trade.analytics.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.analytics.mapper.AnalyticsMapper;
import com.idlefish.trade.analytics.narrative.vo.DashboardOverviewVO;
import com.idlefish.trade.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DashboardService 编排测试：mock AnalyticsMapper 与 StringRedisTemplate（best-effort 缓存），
 * 验证结论卡 / 健康度 / 异常 / 贡献度的派生与 Redis 写入路径。离线、无 Spring 上下文、无 MySQL。
 */
class DashboardServiceTest {

    private AnalyticsMapper mapper;
    private ValueOperations<String, String> ops;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AnalyticsMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null); // 缓存未命中 -> 实时计算
        service = new DashboardService(mapper, redis, new ObjectMapper());
    }

    @Test
    void overview_builds_six_cards_and_health() {
        when(mapper.overviewTotals(any())).thenReturn(Map.of(
                "gmv", 1_000_00L, "paidOrderCount", 10L, "refundOrderCount", 1L, "totalOrderCount", 12L));
        when(mapper.userCount()).thenReturn(100L);
        when(mapper.itemOnsaleCount()).thenReturn(50L);
        when(mapper.categoryGmv(any())).thenReturn(List.of(
                Map.of("categoryName", "手机", "gmv", 600_00L),
                Map.of("categoryName", "数码", "gmv", 400_00L)));

        DashboardOverviewVO vo = service.overview(30);

        assertEquals(6, vo.cards().size());
        assertNotNull(vo.health());
        assertNotNull(vo.anomalies());
        // 结论卡含 GMV 与退款率
        assertTrue(vo.cards().stream().anyMatch(c -> "gmv".equals(c.key())));
        assertTrue(vo.cards().stream().anyMatch(c -> "refundRate".equals(c.key())));
        // 缓存写入被触发
        verify(ops, atLeastOnce()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void contribution_returns_shares_sum_to_one() {
        when(mapper.categoryGmv(any())).thenReturn(List.of(
                Map.of("categoryName", "手机", "gmv", 600_00L),
                Map.of("categoryName", "数码", "gmv", 400_00L)));

        List<ContributionAnalyzer.Contribution> c = service.contribution(30, "category");
        double sum = c.stream().mapToDouble(ContributionAnalyzer.Contribution::share).sum();
        assertEquals(1.0, sum, 1e-9);
        assertTrue(c.get(0).share() >= c.get(1).share());
    }

    @Test
    void contribution_invalid_dimension_throws() {
        assertThrows(BizException.class, () -> service.contribution(30, "user"));
    }

    @Test
    void anomalies_endpoint_does_not_throw_on_empty_data() {
        when(mapper.overviewTotals(any())).thenReturn(Map.of(
                "gmv", 0L, "paidOrderCount", 0L, "refundOrderCount", 0L, "totalOrderCount", 0L));
        when(mapper.categoryGmv(any())).thenReturn(List.of());
        assertDoesNotThrow(() -> service.anomalies(30));
    }
}
