package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 可观测性守卫单测（T01，REQ-06/07 防御网）：直测 scanStuckUnpaid / scanDuplicateFundFlow / guard 早退。
 * 纯 Mockito，离线可跑，不依赖 MySQL 上下文。
 */
@ExtendWith(MockitoExtension.class)
class ObservabilityGuardServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private FundFlowMapper fundFlowMapper;

    private MetricsRegistry metrics;
    private IdlefishProperties props;

    @BeforeEach
    void setup() {
        metrics = new MetricsRegistry();
        props = new IdlefishProperties();
        props.getObservability().setEnabled(true);
        props.getObservability().setStuckOrderMinutes(30);
    }

    private ObservabilityGuardService service() {
        return new ObservabilityGuardService(orderMapper, fundFlowMapper, metrics, props);
    }

    private Order order(String no) {
        Order o = new Order();
        o.setOrderNo(no);
        return o;
    }

    // ===== REQ-06：超时未支付卡单 =====

    @Test
    void scanStuckUnpaid_countsNewOrdersOnce() {
        when(orderMapper.listUnpaidOlderThan(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(order("O1"), order("O2"), order("O3")));
        ObservabilityGuardService s = service();
        s.scanStuckUnpaid(30);
        assertEquals(3L, metrics.counter("pay.callback.timeout"));
    }

    @Test
    void scanStuckUnpaid_edgeDedupNoDoubleCount() {
        when(orderMapper.listUnpaidOlderThan(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(order("O1"), order("O2")));
        ObservabilityGuardService s = service();
        s.scanStuckUnpaid(30);
        assertEquals(2L, metrics.counter("pay.callback.timeout"));
        // 同一批卡单再次扫描：边沿去重，不应重复计数
        s.scanStuckUnpaid(30);
        assertEquals(2L, metrics.counter("pay.callback.timeout"));
    }

    @Test
    void scanStuckUnpaid_recoversThenRealerts() {
        when(orderMapper.listUnpaidOlderThan(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(order("O1")));
        ObservabilityGuardService s = service();
        s.scanStuckUnpaid(30);
        assertEquals(1L, metrics.counter("pay.callback.timeout"));
        // 卡单恢复（空）：集合清空
        when(orderMapper.listUnpaidOlderThan(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptyList());
        s.scanStuckUnpaid(30);
        assertEquals(1L, metrics.counter("pay.callback.timeout"));
        // 再次卡住：重新计数（避免永久虚高后无法再告警）
        when(orderMapper.listUnpaidOlderThan(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(order("O1")));
        s.scanStuckUnpaid(30);
        assertEquals(2L, metrics.counter("pay.callback.timeout"));
    }

    // ===== REQ-07：DB 级重复资金流水 =====

    @Test
    void scanDuplicateFundFlow_countsNewBizNoOnce() {
        when(fundFlowMapper.listDuplicateBizNos()).thenReturn(List.of("BIZ-A", "BIZ-B"));
        ObservabilityGuardService s = service();
        s.scanDuplicateFundFlow();
        assertEquals(2L, metrics.counter("fund.flow.duplicate"));
        // 重复扫描同批：去重
        s.scanDuplicateFundFlow();
        assertEquals(2L, metrics.counter("fund.flow.duplicate"));
    }

    @Test
    void scanDuplicateFundFlow_emptyNoCount() {
        when(fundFlowMapper.listDuplicateBizNos()).thenReturn(Collections.emptyList());
        ObservabilityGuardService s = service();
        s.scanDuplicateFundFlow();
        assertEquals(0L, metrics.counter("fund.flow.duplicate"));
    }

    // ===== guard() 总开关早退 =====

    @Test
    void guard_returnsEarlyWhenDisabled() {
        props.getObservability().setEnabled(false);
        ObservabilityGuardService s = service();
        s.guard();
        // 未扫描，任何计数器保持 0
        assertEquals(0L, metrics.counter("pay.callback.timeout"));
        assertEquals(0L, metrics.counter("fund.flow.duplicate"));
    }

    @Test
    void guard_enabledTriggersScans() {
        when(orderMapper.listUnpaidOlderThan(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(order("O1")));
        when(fundFlowMapper.listDuplicateBizNos()).thenReturn(List.of("BIZ-A"));
        ObservabilityGuardService s = service();
        s.guard();
        assertEquals(1L, metrics.counter("pay.callback.timeout"));
        assertEquals(1L, metrics.counter("fund.flow.duplicate"));
        assertTrue(props.getObservability().isEnabled());
    }
}
