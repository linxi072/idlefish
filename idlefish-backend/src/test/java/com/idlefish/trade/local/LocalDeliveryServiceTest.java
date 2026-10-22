package com.idlefish.trade.local;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.local.dto.LocalDeliveryApplyRequest;
import com.idlefish.trade.local.entity.LocalDelivery;
import com.idlefish.trade.local.mapper.LocalDeliveryMapper;
import com.idlefish.trade.local.provider.DispatchCommand;
import com.idlefish.trade.local.provider.DispatchResult;
import com.idlefish.trade.local.provider.LocalFulfillmentProvider;
import com.idlefish.trade.local.provider.TrackPoint;
import com.idlefish.trade.local.service.LocalDeliveryService;
import com.idlefish.trade.local.vo.LocalAvailabilityVO;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.mapper.LogisticsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 同城配送服务单测（ITER-F05 / REQ-06、REQ-07、REQ-09）。
 * 覆盖：四层可用性预检、服务商未接入明确不可用、调度失败不伪造成功、
 * 运费计算、轨迹回传与「无运单号/无轨迹」的明确失败。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalDeliveryServiceTest {

    @Mock
    private LocalDeliveryMapper deliveryMapper;
    @Mock
    private LogisticsMapper logisticsMapper;
    @Mock
    private LocalFulfillmentProvider provider;
    @Mock
    private NotificationService notificationService;

    private IdlefishProperties props;
    private LocalDeliveryService service;

    @BeforeEach
    void setUp() {
        props = new IdlefishProperties();
        props.getLocal().setEnabled(true);
        props.getLocal().setCities(List.of("杭州"));
        props.getLocal().setDeliveryBaseFeeFen(600L);
        props.getLocal().setDeliveryBaseKm(3.0);
        props.getLocal().setDeliveryPerKmFen(200L);
        props.getLocal().setDeliveryMaxKm(30.0);
        service = new LocalDeliveryService(deliveryMapper, logisticsMapper, provider, props,
                new MetricsRegistry(), notificationService, new ObjectMapper());
    }

    private LocalDeliveryApplyRequest req() {
        LocalDeliveryApplyRequest r = new LocalDeliveryApplyRequest();
        r.setBizType("ORDER");
        r.setBizNo("ORD1");
        r.setCity("杭州");
        r.setFromAddress("西湖区文一路xx小区3号楼502室");
        r.setToAddress("滨江区江南大道xx苑1号楼301");
        r.setDistanceKm(5.0);
        r.setPickupContact("13812341234");
        r.setReceiverContact("13956785678");
        return r;
    }

    // ===== REQ-09 可用性预检 =====

    @Test
    @DisplayName("REQ-09 服务商未接入 → available=false 且带明确原因")
    void check_providerNotConfigured() {
        when(provider.configured()).thenReturn(false);
        LocalAvailabilityVO v = service.check("杭州", 5.0);
        assertFalse(v.isAvailable());
        assertNotNull(v.getReason());
        assertTrue(v.getReason().contains("服务商"));
    }

    @Test
    @DisplayName("REQ-09 城市未开通 → 明确不可用")
    void check_cityUnsupported() {
        LocalAvailabilityVO v = service.check("北京", 5.0);
        assertFalse(v.isAvailable());
    }

    @Test
    @DisplayName("REQ-09 超范围 → 明确不可用并说明上限")
    void check_distanceExceeded() {
        when(provider.configured()).thenReturn(true);
        LocalAvailabilityVO v = service.check("杭州", 35.0);
        assertFalse(v.isAvailable());
        assertTrue(v.getReason().contains("30"));
    }

    @Test
    @DisplayName("全部通过 → 可用并给出运费预估（分）")
    void check_okWithFreight() {
        when(provider.configured()).thenReturn(true);
        LocalAvailabilityVO v = service.check("杭州", 5.0);
        assertTrue(v.isAvailable());
        assertTrue(v.isProviderReady());
        // 5 公里：600 起步 + 2×200 里程 = 1000 分
        assertEquals(1000L, v.getFreightFen());
    }

    @Test
    @DisplayName("REQ-07 运费预估：3 公里内只收起步价")
    void freight_withinBaseKm() {
        assertEquals(600L, service.freight(2.5));
        assertEquals(600L, service.freight(null));
    }

    // ===== REQ-06 创建配送单 =====

    @Test
    @DisplayName("REQ-06 下单成功：落库 ACCEPTED、第三方运单号回填、地址电话脱敏、写入初始轨迹")
    void apply_success() {
        when(provider.configured()).thenReturn(true);
        when(provider.name()).thenReturn("local-provider");
        when(provider.dispatch(any(DispatchCommand.class)))
                .thenReturn(DispatchResult.ok("PRV123", "张师傅", "13712341234"));

        LocalDelivery d = service.apply(7L, req());

        ArgumentCaptor<LocalDelivery> cap = ArgumentCaptor.forClass(LocalDelivery.class);
        verify(deliveryMapper, times(1)).insert(cap.capture());
        LocalDelivery created = cap.getAllValues().get(0);
        // PRD §九 脱敏
        assertFalse(created.getFromAddress().contains("502"));
        assertEquals("138****1234", LocalFulfillmentRules.maskPhone("13812341234"));
        // REQ-06 订单同城配送写入既有 t_logistics
        verify(logisticsMapper).selectOne(any(LambdaQueryWrapper.class));
        assertNotNull(d.getProviderNo());
    }

    @Test
    @DisplayName("REQ-09 调度失败 → 置 FAILED 并抛明确不可用，绝不保留假成功单")
    void apply_dispatchFailed() {
        when(provider.configured()).thenReturn(true);
        when(provider.name()).thenReturn("local-provider");
        when(provider.dispatch(any(DispatchCommand.class)))
                .thenReturn(DispatchResult.fail("该区域无可用骑手"));

        BizException ex = assertThrows(BizException.class, () -> service.apply(7L, req()));
        assertEquals(Code.LOCAL_DELIVERY_UNAVAILABLE.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("无可用骑手"));

        ArgumentCaptor<LocalDelivery> cap = ArgumentCaptor.forClass(LocalDelivery.class);
        verify(deliveryMapper, times(1)).updateById(cap.capture());
        LocalDelivery failed = cap.getAllValues().get(0);
        assertEquals(LocalDeliveryStateMachine.FAILED, failed.getStatus());
        assertNotNull(failed.getFailReason());
        // 失败时不应写任何物流轨迹
        verify(logisticsMapper, never()).insert(any(com.idlefish.trade.trade.entity.Logistics.class));
    }

    @Test
    @DisplayName("REQ-09 服务商未配置 → 直接拒绝下单（不产生本地假运单）")
    void apply_providerUnavailable() {
        when(provider.configured()).thenReturn(false);
        BizException ex = assertThrows(BizException.class, () -> service.apply(7L, req()));
        assertEquals(Code.LOCAL_DELIVERY_UNAVAILABLE.getCode(), ex.getCode());
        verify(deliveryMapper, never()).insert(any(LocalDelivery.class));
    }

    @Test
    @DisplayName("业务单号缺失 → 参数错误")
    void apply_missingBizNo() {
        LocalDeliveryApplyRequest r = req();
        r.setBizNo(null);
        BizException ex = assertThrows(BizException.class, () -> service.apply(7L, r));
        assertEquals(Code.PARAM_MISSING.getCode(), ex.getCode());
    }

    // ===== 状态推进 =====

    @Test
    @DisplayName("送达后签收：写入 signed 轨迹并置终态")
    void sign_afterArrived() {
        LocalDelivery d = delivery(LocalDeliveryStateMachine.ARRIVED);
        when(deliveryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(d);

        service.sign("LD1");

        ArgumentCaptor<LocalDelivery> cap = ArgumentCaptor.forClass(LocalDelivery.class);
        verify(deliveryMapper).updateById(cap.capture());
        assertEquals(LocalDeliveryStateMachine.SIGNED, cap.getValue().getStatus());
        verify(logisticsMapper).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("非送达态签收被拒绝")
    void sign_invalidStage() {
        LocalDelivery d = delivery(LocalDeliveryStateMachine.CREATED);
        when(deliveryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(d);
        BizException ex = assertThrows(BizException.class, () -> service.sign("LD1"));
        assertEquals(Code.LOCAL_DELIVERY_STATE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("取消：同步取消第三方运单（best-effort）")
    void cancel_syncsProvider() {
        LocalDelivery d = delivery(LocalDeliveryStateMachine.ACCEPTED);
        d.setProviderNo("PRV123");
        when(deliveryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(d);
        when(provider.cancel(anyString())).thenReturn(true);

        service.cancel(7L, "LD1");

        ArgumentCaptor<LocalDelivery> cap = ArgumentCaptor.forClass(LocalDelivery.class);
        verify(deliveryMapper).updateById(cap.capture());
        assertEquals(LocalDeliveryStateMachine.CANCELED, cap.getValue().getStatus());
        verify(provider).cancel("PRV123");
    }

    @Test
    @DisplayName("签收后不可取消")
    void cancel_afterSignBlocked() {
        LocalDelivery d = delivery(LocalDeliveryStateMachine.SIGNED);
        when(deliveryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(d);
        BizException ex = assertThrows(BizException.class, () -> service.cancel(7L, "LD1"));
        assertEquals(Code.LOCAL_DELIVERY_STATE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("配送单不存在 → LOCAL_DELIVERY_NOT_FOUND")
    void notFound() {
        when(deliveryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.sign("LD1"));
        assertEquals(Code.LOCAL_DELIVERY_NOT_FOUND.getCode(), ex.getCode());
    }

    // ===== REQ-06 轨迹 =====

    @Test
    @DisplayName("REQ-06 轨迹回传：第三方节点归一化为结构化时间轴（最新在前）")
    void track_success() {
        LocalDelivery d = delivery(LocalDeliveryStateMachine.DELIVERING);
        d.setProviderNo("PRV123");
        when(deliveryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(d);
        when(provider.configured()).thenReturn(true);
        when(provider.fetchTrack("PRV123")).thenReturn(List.of(
                point("2026-10-22 09:00:00", "骑手已接单"),
                point("2026-10-22 09:30:00", "已取货，正在派送"),
                point("2026-10-22 10:00:00", "已签收")));

        com.idlefish.trade.local.vo.LocalDeliveryVO vo = service.track("LD1");

        assertEquals(3, vo.getTracks().size());
        // 最新节点在前
        assertTrue(vo.getTracks().get(0).getDesc().contains("已签收"));
        assertEquals("sign", vo.getTracks().get(0).getType());
        assertEquals("transit", vo.getTracks().get(1).getType());
        // 回写 t_logistics
        verify(logisticsMapper).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("REQ-09 服务商未接入时查询轨迹 → 明确不可用（不返回空数组伪造成功）")
    void track_providerUnavailable() {
        LocalDelivery d = delivery(LocalDeliveryStateMachine.DELIVERING);
        d.setProviderNo("PRV123");
        when(deliveryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(d);
        when(provider.configured()).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.track("LD1"));
        assertEquals(Code.LOCAL_DELIVERY_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("REQ-09 尚未产生运单号 → 明确不可用")
    void track_noProviderNo() {
        LocalDelivery d = delivery(LocalDeliveryStateMachine.CREATED);
        when(deliveryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(d);
        when(provider.configured()).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.track("LD1"));
        assertEquals(Code.LOCAL_DELIVERY_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("REQ-09 第三方返回空轨迹 → 明确不可用")
    void track_emptyPoints() {
        LocalDelivery d = delivery(LocalDeliveryStateMachine.DELIVERING);
        d.setProviderNo("PRV123");
        when(deliveryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(d);
        when(provider.configured()).thenReturn(true);
        when(provider.fetchTrack("PRV123")).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.track("LD1"));
        assertEquals(Code.LOCAL_DELIVERY_UNAVAILABLE.getCode(), ex.getCode());
    }

    // ===== 辅助 =====

    private LocalDelivery delivery(String status) {
        LocalDelivery d = new LocalDelivery();
        d.setId(1L);
        d.setDeliveryNo("LD1");
        d.setUserId(7L);
        d.setBizType("ORDER");
        d.setBizNo("ORD1");
        d.setCity("杭州");
        d.setProviderName("local-provider");
        d.setFreight(1000L);
        d.setStatus(status);
        return d;
    }

    private TrackPoint point(String time, String desc) {
        TrackPoint p = new TrackPoint();
        p.setTime(time);
        p.setDesc(desc);
        return p;
    }
}
