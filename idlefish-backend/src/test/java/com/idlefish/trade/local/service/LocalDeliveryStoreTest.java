package com.idlefish.trade.local.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.local.entity.LocalDelivery;
import com.idlefish.trade.local.mapper.LocalDeliveryMapper;
import com.idlefish.trade.trade.entity.Logistics;
import com.idlefish.trade.trade.mapper.LogisticsMapper;
import com.idlefish.trade.trade.vo.LogisticsTrack;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 同城配送数据访问层单测（ITER-F05）。覆盖配送单读写/分页、物流轨迹读写与 JSON 序列化降级。
 * 纯 Mockito 离线范式；走 MP Lambda 条件构造器，故为涉及实体 initTableInfo。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalDeliveryStoreTest {

    @Mock
    private LocalDeliveryMapper deliveryMapper;
    @Mock
    private LogisticsMapper logisticsMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LocalDeliveryStore store;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "dummy");
        TableInfoHelper.initTableInfo(assistant, LocalDelivery.class);
        TableInfoHelper.initTableInfo(assistant, Logistics.class);
    }

    @BeforeEach
    void setUp() {
        store = new LocalDeliveryStore(deliveryMapper, logisticsMapper, objectMapper);
    }

    private LocalDelivery delivery(String status) {
        LocalDelivery d = new LocalDelivery();
        d.setId(1L);
        d.setDeliveryNo("LD1");
        d.setBizType("ORDER");
        d.setBizNo("ORD1");
        d.setProviderName("local-provider");
        d.setStatus(status);
        return d;
    }

    private Logistics logistics(String orderNo, String detailJson) {
        Logistics l = new Logistics();
        l.setId(9L);
        l.setOrderNo(orderNo);
        l.setCompany("local-provider");
        l.setStatus("transport");
        l.setDetailJson(detailJson);
        return l;
    }

    // ===== 配送单 =====

    @Test
    @DisplayName("selectByDeliveryNo：命中返回实体")
    void selectByDeliveryNo_found() {
        when(deliveryMapper.selectOne(any())).thenReturn(delivery("CREATED"));
        LocalDelivery d = store.selectByDeliveryNo("LD1");
        assertNotNull(d);
        assertEquals("LD1", d.getDeliveryNo());
        verify(deliveryMapper).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("selectByDeliveryNo：未命中抛 LOCAL_DELIVERY_NOT_FOUND")
    void selectByDeliveryNo_notFound() {
        when(deliveryMapper.selectOne(any())).thenReturn(null);
        assertThrows(com.idlefish.trade.common.BizException.class, () -> store.selectByDeliveryNo("LDX"));
    }

    @Test
    @DisplayName("insert/updateById：委托 mapper")
    void insert_and_update() {
        LocalDelivery d = delivery("CREATED");
        store.insert(d);
        store.updateById(d);
        verify(deliveryMapper, times(1)).insert((LocalDelivery) any());
        verify(deliveryMapper, times(1)).updateById((LocalDelivery) any());
    }

    @Test
    @DisplayName("selectPageByUser：分页委托且按用户/时间倒序")
    void selectPageByUser() {
        Page<LocalDelivery> page = new Page<>(1, 10);
        when(deliveryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        IPage<LocalDelivery> r = store.selectPageByUser(7L, 1, 10);
        assertNotNull(r);
        verify(deliveryMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("adminSelectPage：条件为空也走分页")
    void adminSelectPage() {
        Page<LocalDelivery> page = new Page<>(1, 10);
        when(deliveryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        IPage<LocalDelivery> r = store.adminSelectPage(1, 10, null, null, null);
        assertNotNull(r);
        verify(deliveryMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    // ===== 物流轨迹 =====

    @Test
    @DisplayName("selectLogisticsByOrderNo：命中返回")
    void selectLogisticsByOrderNo_found() {
        when(logisticsMapper.selectOne(any())).thenReturn(logistics("ORD1", "[]"));
        Logistics l = store.selectLogisticsByOrderNo("ORD1");
        assertNotNull(l);
        assertEquals("ORD1", l.getOrderNo());
    }

    @Test
    @DisplayName("insertLogistics/updateLogistics：委托 mapper")
    void insert_and_update_logistics() {
        Logistics l = logistics("ORD1", "[]");
        store.insertLogistics(l);
        store.updateLogistics(l);
        verify(logisticsMapper, times(1)).insert((Logistics) any());
        verify(logisticsMapper, times(1)).updateById((Logistics) any());
    }

    @Test
    @DisplayName("serializeNodes：正常序列化为 JSON 数组")
    void serializeNodes_ok() {
        Map<String, String> m = LocalDeliveryViews.node(java.time.LocalDateTime.of(2026, 10, 22, 9, 0), "已创建");
        String json = store.serializeNodes(List.of(m));
        assertTrue(json.contains("已创建"));
        assertTrue(json.startsWith("["));
    }

    @Test
    @DisplayName("deserializeNodes：非法 JSON 降级为空")
    void deserializeNodes_invalid() {
        List<Map<String, String>> r = store.deserializeNodes("not-a-json");
        assertTrue(r.isEmpty());
    }

    @Test
    @DisplayName("persistOrderTrack：无既有物流 → 新增，状态映射为 transport")
    void persistOrderTrack_insert() {
        when(logisticsMapper.selectOne(any())).thenReturn(null);
        store.persistOrderTrack("ORD1", "LD1", "local-provider", "CREATED");
        ArgumentCaptor<Logistics> cap = ArgumentCaptor.forClass(Logistics.class);
        verify(logisticsMapper).insert(cap.capture());
        Logistics l = cap.getValue();
        assertEquals("ORD1", l.getOrderNo());
        assertEquals("local-provider", l.getCompany());
        assertEquals("transport", l.getStatus());
        assertNotNull(l.getDetailJson());
    }

    @Test
    @DisplayName("persistOrderTrack：既有物流 → 更新，传入节点生效")
    void persistOrderTrack_updateWithNodes() {
        when(logisticsMapper.selectOne(any())).thenReturn(logistics("ORD1", "[]"));
        Map<String, String> node = LocalDeliveryViews.node(java.time.LocalDateTime.of(2026, 10, 22, 9, 0), "已签收");
        store.persistOrderTrack("ORD1", "LD1", "local-provider", "SIGNED", List.of(node));
        ArgumentCaptor<Logistics> cap = ArgumentCaptor.forClass(Logistics.class);
        verify(logisticsMapper).updateById(cap.capture());
        Logistics l = cap.getValue();
        assertEquals("signed", l.getStatus());
        assertTrue(l.getDetailJson().contains("已签收"));
    }

    @Test
    @DisplayName("readOrderTracks：有详情 → 渲染为时间轴")
    void readOrderTracks_withDetail() {
        String json = "[{\"time\":\"2026-10-22 09:00:00\",\"desc\":\"已签收\"}]";
        when(logisticsMapper.selectOne(any())).thenReturn(logistics("ORD1", json));
        List<LogisticsTrack> tracks = store.readOrderTracks("ORD1", "local-provider");
        assertNotNull(tracks);
        assertTrue(tracks.size() >= 1);
    }

    @Test
    @DisplayName("readOrderTracks：无物流/空详情 → 空列表")
    void readOrderTracks_empty() {
        when(logisticsMapper.selectOne(any())).thenReturn(null);
        assertTrue(store.readOrderTracks("ORDX", "local-provider").isEmpty());

        when(logisticsMapper.selectOne(any())).thenReturn(logistics("ORD1", "   "));
        assertTrue(store.readOrderTracks("ORD1", "local-provider").isEmpty());
    }
}
