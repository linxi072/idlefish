package com.idlefish.trade.trade.service;

import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对账告警单测（F-11.4）：matched 不告警，diff≠0 触发系统告警。
 * 手动构造服务（含 @Value 的 alertAdminUserId），纯 Mockito，离线可跑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReconciliationServiceTest {

    @Mock private PayOrderMapper payOrderMapper;
    @Mock private FundFlowMapper fundFlowMapper;
    @Mock private NotificationService notificationService;

    private ReconciliationService service() {
        return new ReconciliationService(payOrderMapper, fundFlowMapper, notificationService, 1L);
    }

    @Test
    void matched_does_not_alert() {
        LocalDateTime now = LocalDateTime.now();
        PayOrder po = new PayOrder();
        po.setStatus("paid");
        po.setAmount(100L);
        po.setPaidAt(now);
        when(payOrderMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(po));

        FundFlow flow = new FundFlow();
        flow.setType("PAY");
        flow.setAmount(100L);
        flow.setCreatedAt(now);
        when(fundFlowMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(flow));

        Map<String, Object> report = service().reconcileWithAlert(LocalDate.now());

        assertTrue((Boolean) report.get("matched"));
        verify(notificationService, never()).notify(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void mismatch_triggers_system_alert() {
        LocalDateTime now = LocalDateTime.now();
        PayOrder po = new PayOrder();
        po.setStatus("paid");
        po.setAmount(100L);
        po.setPaidAt(now);
        when(payOrderMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(po));

        FundFlow flow = new FundFlow();
        flow.setType("PAY");
        flow.setAmount(80L);
        flow.setCreatedAt(now);
        when(fundFlowMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(flow));

        Map<String, Object> report = service().reconcileWithAlert(LocalDate.now());

        assertFalse((Boolean) report.get("matched"));
        verify(notificationService).notify(ArgumentMatchers.eq(1L),
                ArgumentMatchers.eq(NotificationType.SYSTEM_ALERT), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
    }
}
