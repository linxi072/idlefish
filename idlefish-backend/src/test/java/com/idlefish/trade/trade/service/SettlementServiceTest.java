package com.idlefish.trade.trade.service;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.risk.entity.RiskEvent;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Settlement;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.SettlementMapper;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.trade.service.FundEscrowService;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算服务单测（#6 资金链路单测补全）：processDue 放款 / 风控冻结 / 并发 CAS 双保险。
 * Mock 全部依赖，无 Spring、无 DB，离线可跑（mvn -o -Plocal test）。
 * 注：PayService 回调幂等已由 PayCallbackTest 覆盖，本类聚焦 SettlementService。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementServiceTest {

    @Mock private SettlementMapper settlementMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private FundFlowMapper fundFlowMapper;
    @Mock private RiskEventMapper riskEventMapper;
    @Mock private FundEscrowService escrow;
    @Mock private IdlefishProperties props;
    @Mock private NotificationService notificationService;
    @Mock private MetricsRegistry metrics;
    @InjectMocks private SettlementService settlementService;

    private final AtomicInteger flowInserts = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        flowInserts.set(0);
        when(fundFlowMapper.insert(any(FundFlow.class))).thenAnswer(inv -> {
            flowInserts.incrementAndGet();
            return 1;
        });
    }

    /** 构造一笔已到期、待结算的结算单。 */
    private Settlement dueSettlement() {
        Settlement s = new Settlement();
        s.setId(10L);
        s.setSellerId(1001L);
        s.setStatus("pending");
        s.setAmount(950L);
        s.setSettleNo("S1");
        s.setOrderNo("NO1");
        s.setSettleAt(LocalDateTime.now().minusDays(1));
        return s;
    }

    @Test
    @DisplayName("结算放款：到期 pending 单正常放款（CAS 认领 + 资金流水 1 条 + 卖家通知）")
    void processDue_settlesPending() {
        when(settlementMapper.selectList(any())).thenReturn(List.of(dueSettlement()));
        when(riskEventMapper.selectList(any())).thenReturn(List.of());
        when(settlementMapper.update(any(), any())).thenReturn(1);

        settlementService.processDue();

        assertEquals(1, flowInserts.get());
        verify(notificationService, times(1)).notify(any(), any(), any(), any(), any());
        verify(metrics, times(1)).increment("settle.settled");
    }

    @Test
    @DisplayName("风控冻结：卖家存在未处置高危事件时暂缓放款（置 frozen，不写资金流水）")
    void processDue_frozenByRisk() {
        when(settlementMapper.selectList(any())).thenReturn(List.of(dueSettlement()));
        RiskEvent r = new RiskEvent();
        r.setUserId(1001L);
        r.setStatus("open");
        when(riskEventMapper.selectList(any())).thenReturn(List.of(r));
        when(settlementMapper.update(any(), any())).thenReturn(1);

        settlementService.processDue();

        assertEquals(0, flowInserts.get());
        // 应发起一次 update，且传入实体状态为 frozen（而非 settled）
        ArgumentCaptor<Settlement> cap = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper, times(1)).update(cap.capture(), any());
        assertEquals("frozen", cap.getValue().getStatus());
    }

    @Test
    @DisplayName("并发双保险：CAS 认领失败（affected=0）时不重复放款")
    void processDue_casMissNoDoubleSettle() {
        when(settlementMapper.selectList(any())).thenReturn(List.of(dueSettlement()));
        when(riskEventMapper.selectList(any())).thenReturn(List.of());
        // 模拟并发已被其他实例认领：CAS update 返回 0
        when(settlementMapper.update(any(), any())).thenReturn(0);

        settlementService.processDue();

        assertEquals(0, flowInserts.get());
        verify(notificationService, times(0)).notify(any(), any(), any(), any(), any());
    }
}
