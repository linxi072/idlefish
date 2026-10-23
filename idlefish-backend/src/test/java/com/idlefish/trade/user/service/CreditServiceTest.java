package com.idlefish.trade.user.service;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.ReviewMapper;
import com.idlefish.trade.user.entity.CreditLog;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.CreditLogMapper;
import com.idlefish.trade.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 信用服务单测：聚焦 recompute 幂等重算与 CREDIT_SCORE_CHANGED 领域事件接线（事件驱动架构 T06E）。
 *
 * <p>采用纯 Mockito 离线范式；Calculator 为纯函数，按默认权重构造 IdlefishProperties 即可得出确定分数。
 * 确定性场景：无实名/无订单/无评价/无风控 → compute 仅加 reviewBaseline(15)，故 newScore 恒为 15。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private ReviewMapper reviewMapper;
    @Mock private RiskEventMapper riskEventMapper;
    @Mock private CreditLogMapper creditLogMapper;
    @Mock private com.idlefish.trade.event.DomainEventPublisher eventPublisher;

    /** IdlefishProperties 为普通配置 POJO（非 mock）：手动构造服务，按默认权重注入。 */
    private CreditService service;

    @BeforeEach
    void setUp() {
        service = new CreditService(userMapper, orderMapper, reviewMapper, riskEventMapper,
                creditLogMapper, new IdlefishProperties(), eventPublisher);
    }

    /** 默认权重下、无任何业务事实的确定性重算：newScore = reviewBaseline = 15。 */
    private User baseUser(Long id, Integer creditScore) {
        User u = new User();
        u.setId(id);
        u.setStatus(0);            // 未封禁
        u.setRealNameVerified(0);  // 未实名
        u.setCreditScore(creditScore);
        return u;
    }

    private void stubZeroFactors() {
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        when(riskEventMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.updateById((User) any())).thenReturn(1);
        when(creditLogMapper.insert((CreditLog) any())).thenReturn(1);
    }

    @Test
    void recompute_publishesEventWhenScoreChanged() {
        // 旧分 100 ≠ 新分 15 → 应发布 CREDIT_SCORE_CHANGED
        when(userMapper.selectById(1L)).thenReturn(baseUser(1L, 100));
        stubZeroFactors();

        int score = service.recompute(1L);

        assertEquals(15, score);
        verify(creditLogMapper, times(1)).insert(any(CreditLog.class));
        verify(eventPublisher, times(1)).publish(
                eq(com.idlefish.trade.event.EventType.CREDIT_SCORE_CHANGED),
                eq("1"), any());
    }

    @Test
    void recompute_skipsEventWhenScoreUnchanged() {
        // 旧分 15 == 新分 15 → 幂等重算不产生噪声事件
        when(userMapper.selectById(1L)).thenReturn(baseUser(1L, 15));
        stubZeroFactors();

        int score = service.recompute(1L);

        assertEquals(15, score);
        verify(creditLogMapper, times(1)).insert(any(CreditLog.class));
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void recompute_returnsZeroWhenUserNotFound() {
        when(userMapper.selectById(1L)).thenReturn(null);

        int score = service.recompute(1L);

        assertEquals(0, score);
        verify(userMapper, never()).updateById((User) any());
        verify(creditLogMapper, never()).insert((CreditLog) any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }
}
