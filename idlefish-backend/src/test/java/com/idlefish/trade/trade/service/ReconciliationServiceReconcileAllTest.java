package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.idlefish.trade.common.enums.PayStatus;
import com.idlefish.trade.common.enums.RefundStatus;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.entity.ReconcileBill;
import com.idlefish.trade.trade.entity.ReconcileDiff;
import com.idlefish.trade.trade.entity.Refund;
import com.idlefish.trade.trade.entity.Settlement;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.trade.mapper.ReconcileBillMapper;
import com.idlefish.trade.trade.mapper.ReconcileDiffMapper;
import com.idlefish.trade.trade.mapper.RefundMapper;
import com.idlefish.trade.trade.mapper.SettlementMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多维对账服务单测（ITER-T02 / REQ-06/07/08）：覆盖平账落库、差异工单与告警、人工处置粘性。
 * 纯 Mockito，离线可跑。fundFlow 按 type 分支返回，模拟多维实际侧数据。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReconciliationServiceReconcileAllTest {

    @BeforeAll
    static void initMpMetadata() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "dummy");
        TableInfoHelper.initTableInfo(assistant, PayOrder.class);
        TableInfoHelper.initTableInfo(assistant, FundFlow.class);
        TableInfoHelper.initTableInfo(assistant, Refund.class);
        TableInfoHelper.initTableInfo(assistant, Settlement.class);
        TableInfoHelper.initTableInfo(assistant, ReconcileBill.class);
        TableInfoHelper.initTableInfo(assistant, ReconcileDiff.class);
    }

    @Mock private PayOrderMapper payOrderMapper;
    @Mock private FundFlowMapper fundFlowMapper;
    @Mock private RefundMapper refundMapper;
    @Mock private SettlementMapper settlementMapper;
    @Mock private ReconcileBillMapper reconcileBillMapper;
    @Mock private ReconcileDiffMapper reconcileDiffMapper;
    @Mock private NotificationService notificationService;

    /** 按 FundFlow.type 分支返回：仅指定维度返回 flow，其余返回空（模拟多维实际侧）。 */
    private void stubFlowByType(FundFlow flow, String type) {
        when(fundFlowMapper.selectList(ArgumentMatchers.any())).thenAnswer(inv -> {
            LambdaQueryWrapper<FundFlow> w = inv.getArgument(0);
            w.getTargetSql(); // 触发参数解析，填充 paramNameValuePairs
            String t = (String) w.getParamNameValuePairs().values().stream()
                    .filter(v -> v instanceof String && Set.of("PAY", "REFUND", "SETTLE").contains(v))
                    .findFirst().orElse("");
            return type.equals(t) ? List.of(flow) : List.of();
        });
    }

    private ReconciliationService service() {
        return new ReconciliationService(payOrderMapper, fundFlowMapper, refundMapper, settlementMapper,
                reconcileBillMapper, reconcileDiffMapper, notificationService, new MetricsRegistry(), 1L);
    }

    @Test
    void reconcileAll_all_matched_persists_three_bills_no_alert() {
        LocalDateTime now = LocalDateTime.now();
        PayOrder po = new PayOrder();
        po.setStatus(PayStatus.SUCCESS.getCode());
        po.setAmount(100L);
        po.setPaidAt(now);
        po.setOrderNo("O1");
        when(payOrderMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(po));

        FundFlow flow = new FundFlow();
        flow.setType("PAY");
        flow.setBizNo("O1");
        flow.setAmount(100L);
        flow.setCreatedAt(now);
        stubFlowByType(flow, "PAY");
        when(refundMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of());
        when(settlementMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of());

        List<ReconcileBill> bills = service().reconcileAll(LocalDate.now().minusDays(1));

        assertEquals(3, bills.size());
        assertTrue(bills.stream().allMatch(ReconcileBill::getMatched));
        verify(reconcileBillMapper, times(3)).insert(ArgumentMatchers.any(ReconcileBill.class));
        verify(reconcileDiffMapper, never()).insert(ArgumentMatchers.any(ReconcileDiff.class));
        verify(notificationService, never()).notify(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void reconcileAll_pay_mismatch_persists_diff_and_alerts() {
        LocalDateTime now = LocalDateTime.now();
        PayOrder po = new PayOrder();
        po.setStatus(PayStatus.SUCCESS.getCode());
        po.setAmount(100L);
        po.setPaidAt(now);
        po.setOrderNo("O1");
        when(payOrderMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(po));

        FundFlow flow = new FundFlow();
        flow.setType("PAY");
        flow.setBizNo("O1");
        flow.setAmount(80L); // 差异 20 分
        flow.setCreatedAt(now);
        stubFlowByType(flow, "PAY");
        when(refundMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of());
        when(settlementMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of());

        MetricsRegistry metrics = new MetricsRegistry();
        ReconciliationService svc = new ReconciliationService(payOrderMapper, fundFlowMapper, refundMapper,
                settlementMapper, reconcileBillMapper, reconcileDiffMapper, notificationService, metrics, 1L);
        List<ReconcileBill> bills = svc.reconcileAll(LocalDate.now().minusDays(1));

        ReconcileBill payBill = bills.stream()
                .filter(b -> "PAY".equals(b.getDimension())).findFirst().orElseThrow();
        assertFalse(payBill.getMatched(), "PAY 维度应判不平");
        assertEquals(-20L, payBill.getDiffAmount());

        // 差异明细落库一次（O1 金额不符）
        ArgumentCaptor<ReconcileDiff> diffCaptor = ArgumentCaptor.forClass(ReconcileDiff.class);
        verify(reconcileDiffMapper, times(1)).insert(diffCaptor.capture());
        assertEquals("O1", diffCaptor.getValue().getBizNo());
        assertEquals(100L, diffCaptor.getValue().getExpectedAmount());
        assertEquals(80L, diffCaptor.getValue().getActualAmount());

        // 告警与指标
        verify(notificationService).notify(ArgumentMatchers.eq(1L),
                ArgumentMatchers.eq(NotificationType.SYSTEM_ALERT), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
        assertEquals(1L, metrics.counter("reconcile.diff"));
    }

    @Test
    void reconcileAll_respects_manual_resolution_no_realert_no_diff_overwrite() {
        // 预置一笔已被人工处置为 RESOLVED 的 PAY 账单
        ReconcileBill seeded = new ReconcileBill();
        seeded.setId(99L);
        seeded.setReconcileDate(LocalDate.now().minusDays(1));
        seeded.setDimension("PAY");
        seeded.setMatched(false);
        seeded.setAlertSent(true);
        seeded.setStatus("RESOLVED");
        when(reconcileBillMapper.selectOne(ArgumentMatchers.any())).thenAnswer(inv -> {
            LambdaQueryWrapper<ReconcileBill> w = inv.getArgument(0);
            w.getTargetSql(); // 触发参数解析，填充 paramNameValuePairs
            String dim = (String) w.getParamNameValuePairs().values().stream()
                    .filter(v -> v instanceof String && Set.of("PAY", "REFUND", "SETTLE").contains(v))
                    .findFirst().orElse("");
            return "PAY".equals(dim) ? seeded : null;
        });

        LocalDateTime now = LocalDateTime.now();
        PayOrder po = new PayOrder();
        po.setStatus(PayStatus.SUCCESS.getCode());
        po.setAmount(100L);
        po.setPaidAt(now);
        po.setOrderNo("O1");
        when(payOrderMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(po));

        FundFlow flow = new FundFlow();
        flow.setType("PAY");
        flow.setBizNo("O1");
        flow.setAmount(80L); // 仍不平
        flow.setCreatedAt(now);
        stubFlowByType(flow, "PAY");
        when(refundMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of());
        when(settlementMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of());

        service().reconcileAll(LocalDate.now().minusDays(1));

        // 人工已处置：不再重复告警、不再覆盖差异明细；仅更新账单头
        verify(notificationService, never()).notify(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(reconcileDiffMapper, never()).insert(ArgumentMatchers.any(ReconcileDiff.class));
        verify(reconcileBillMapper, atLeastOnce()).updateById(ArgumentMatchers.any(ReconcileBill.class));
    }
}
