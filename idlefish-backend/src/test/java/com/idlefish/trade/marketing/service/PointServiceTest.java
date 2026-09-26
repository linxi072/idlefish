package com.idlefish.trade.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.marketing.entity.Point;
import com.idlefish.trade.marketing.entity.PointLog;
import com.idlefish.trade.marketing.mapper.PointLogMapper;
import com.idlefish.trade.marketing.mapper.PointMapper;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.common.observability.MetricsRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分服务单测（F-13.1）：Mockito 隔离 Mapper / 通知 / 指标，验证抵现、获得、释放与签到幂等。
 * 纯离线可跑（不依赖数据库 / Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private PointMapper pointMapper;
    @Mock
    private PointLogMapper pointLogMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MetricsRegistry metrics;

    private IdlefishProperties props;
    private PointService pointService;

    @BeforeEach
    void setup() {
        props = new IdlefishProperties();
        pointService = new PointService(pointMapper, pointLogMapper, notificationService, metrics, props);
    }

    private Point account(Long balance) {
        Point p = new Point();
        p.setId(1L);
        p.setUserId(1L);
        p.setBalance(balance);
        p.setTotalEarned(0L);
        return p;
    }

    @Test
    @DisplayName("抵现成功：足额积分按规则抵扣并返回抵扣金额")
    void redeemSuccess() {
        when(pointMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(1000L));
        when(pointMapper.update(any(), any())).thenReturn(1);

        long discount = pointService.redeem(1L, 1000L, "O1", 10000L);

        assertEquals(1000L, discount); // 10 元
        verify(pointMapper, atLeastOnce()).update(any(), any());
        ArgumentCaptor<PointLog> cap = ArgumentCaptor.forClass(PointLog.class);
        verify(pointLogMapper).insert(cap.capture());
        assertEquals(-1000L, cap.getValue().getDelta());
        assertEquals(PointService.BIZ_REDEEM, cap.getValue().getBizType());
    }

    @Test
    @DisplayName("并发超兑：余额不足扣减失败抛 POINT_NOT_ENOUGH")
    void redeemInsufficientThrows() {
        when(pointMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(1000L));
        // 模拟并发竞争：原子扣减 0 行（余额已被他人扣走）
        when(pointMapper.update(any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> pointService.redeem(1L, 1000L, "O1", 10000L));
        assertEquals(Code.POINT_NOT_ENOUGH.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("交易得积分：按金额换算并返回获得值，写流水")
    void earnByTrade() {
        when(pointMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(0L));

        long earned = pointService.earnByTrade(1L, "O1", 10000L);

        assertEquals(200L, earned); // 100 元 ×2 分/元 → 200 积分
        ArgumentCaptor<PointLog> cap = ArgumentCaptor.forClass(PointLog.class);
        verify(pointLogMapper).insert(cap.capture());
        assertEquals(200L, cap.getValue().getDelta());
        assertEquals(PointService.BIZ_TRADE, cap.getValue().getBizType());
    }

    @Test
    @DisplayName("关单释放：回滚已抵扣积分并写释放流水")
    void releaseByOrder() {
        PointLog redeemLog = new PointLog();
        redeemLog.setUserId(1L);
        redeemLog.setDelta(-100L);
        when(pointLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(redeemLog));
        when(pointMapper.update(any(), any())).thenReturn(1);
        when(pointMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account(900L));

        pointService.releaseByOrder("O1");

        ArgumentCaptor<PointLog> cap = ArgumentCaptor.forClass(PointLog.class);
        verify(pointLogMapper).insert(cap.capture());
        PointLog rel = cap.getValue();
        assertEquals(100L, rel.getDelta());
        assertEquals(PointService.BIZ_REDEEM_RELEASED, rel.getBizType());
    }

    @Test
    @DisplayName("签到幂等：当日已签到抛业务异常")
    void signinIdempotent() {
        when(pointLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> pointService.signin(1L));
        assertEquals(Code.BIZ_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("关闭开关后不产生积分")
    void disabledNoEarn() {
        props.getPoint().setEnabled(false);
        long earned = pointService.earnByTrade(1L, "O1", 10000L);
        assertEquals(0L, earned);
        verify(pointLogMapper, times(0)).insert(any(PointLog.class));
    }
}
