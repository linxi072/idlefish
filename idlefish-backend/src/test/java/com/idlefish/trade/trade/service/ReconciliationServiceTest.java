package com.idlefish.trade.trade.service;

import com.idlefish.trade.common.enums.PayStatus;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** 纯 Mockito 测试无 Spring/MP 上下文，需手动注册实体元数据，否则 LambdaQueryWrapper 解析列时报「can not find lambda cache」。 */
    @BeforeAll
    static void initMpMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "dummy"), PayOrder.class);
    }

    @Mock private PayOrderMapper payOrderMapper;
    @Mock private FundFlowMapper fundFlowMapper;
    @Mock private NotificationService notificationService;

    private ReconciliationService service(MetricsRegistry metrics) {
        return new ReconciliationService(payOrderMapper, fundFlowMapper, notificationService, metrics, 1L);
    }

    @Test
    void matched_does_not_alert() {
        LocalDateTime now = LocalDateTime.now();
        PayOrder po = new PayOrder();
        po.setStatus(PayStatus.SUCCESS.getCode());
        po.setAmount(100L);
        po.setPaidAt(now);
        when(payOrderMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(po));

        FundFlow flow = new FundFlow();
        flow.setType("PAY");
        flow.setAmount(100L);
        flow.setCreatedAt(now);
        when(fundFlowMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(flow));

        Map<String, Object> report = service(new MetricsRegistry()).reconcileWithAlert(LocalDate.now());

        assertTrue((Boolean) report.get("matched"));
        verify(notificationService, never()).notify(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void mismatch_triggers_system_alert() {
        LocalDateTime now = LocalDateTime.now();
        PayOrder po = new PayOrder();
        po.setStatus(PayStatus.SUCCESS.getCode());
        po.setAmount(100L);
        po.setPaidAt(now);
        when(payOrderMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(po));

        FundFlow flow = new FundFlow();
        flow.setType("PAY");
        flow.setAmount(80L);
        flow.setCreatedAt(now);
        when(fundFlowMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(flow));

        Map<String, Object> report = service(new MetricsRegistry()).reconcileWithAlert(LocalDate.now());

        assertFalse((Boolean) report.get("matched"));
        verify(notificationService).notify(ArgumentMatchers.eq(1L),
                ArgumentMatchers.eq(NotificationType.SYSTEM_ALERT), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    /**
     * REQ-06 回归测试：对账不平且存在已支付订单时，应累加 {@code pay.callback.timeout} 计数器，
     * 供 AlertEvaluator 升级为「支付回调超时/对账缺口」告警。
     */
    @Test
    void mismatch_with_paid_orders_increments_callback_timeout() {
        LocalDateTime now = LocalDateTime.now();
        PayOrder po = new PayOrder();
        po.setStatus(PayStatus.SUCCESS.getCode());
        po.setAmount(100L);
        po.setPaidAt(now);
        when(payOrderMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(po));

        FundFlow flow = new FundFlow();
        flow.setType("PAY");
        flow.setAmount(80L);
        flow.setCreatedAt(now);
        when(fundFlowMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(flow));

        MetricsRegistry reg = new MetricsRegistry();
        Map<String, Object> report = service(reg).reconcileWithAlert(LocalDate.now());

        assertFalse((Boolean) report.get("matched"));
        assertEquals(1L, reg.counter("pay.callback.timeout"),
                "对账不平且存在已支付订单，应累加 pay.callback.timeout");
    }

    /**
     * P0-1 回归测试：对账查询必须按「已支付(success)」状态过滤，
     * 而非历史上误写的 "paid"。通过捕获传给 PayOrderMapper 的查询条件，
     * 校验其 WHERE 子句命中 status 列且取值为 "success"，防止串值缺陷回归。
     */
    @Test
    void queries_by_success_status_not_paid() {
        LocalDateTime now = LocalDateTime.now();
        PayOrder po = new PayOrder();
        po.setStatus(PayStatus.SUCCESS.getCode());
        po.setAmount(100L);
        po.setPaidAt(now);
        when(payOrderMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(po));
        when(fundFlowMapper.selectList(ArgumentMatchers.any())).thenReturn(List.of());

        service(new MetricsRegistry()).reconcile(LocalDate.now());

        ArgumentCaptor<LambdaQueryWrapper<PayOrder>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(payOrderMapper).selectList(captor.capture());
        LambdaQueryWrapper<PayOrder> wrapper = captor.getValue();

        String sql = wrapper.getTargetSql();
        assertNotNull(sql, "query wrapper should produce SQL");
        assertTrue(sql.toLowerCase().contains("status"),
                "查询应过滤 status 列，实际 SQL: " + sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue(PayStatus.SUCCESS.getCode()),
                "查询应过滤 status = success（曾误写为 paid），实际参数: "
                        + wrapper.getParamNameValuePairs());
    }
}
