package com.idlefish.trade.pricing.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.pricing.PriceEstimator;
import com.idlefish.trade.pricing.entity.PriceEstimateAudit;
import com.idlefish.trade.pricing.entity.PriceEstimateConfig;
import com.idlefish.trade.pricing.entity.PriceSample;
import com.idlefish.trade.pricing.mapper.PriceEstimateAuditMapper;
import com.idlefish.trade.pricing.mapper.PriceEstimateConfigMapper;
import com.idlefish.trade.pricing.mapper.PriceSampleMapper;
import com.idlefish.trade.pricing.vo.PriceDeviationVO;
import com.idlefish.trade.pricing.vo.PriceEstimateVO;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.PayOrder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 估价服务单测（REQ-07）：Mockito 离线，覆盖 U01 兜底、估价三路径、偏离、回流、送审、系数覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingServiceTest {

    @Mock
    private PriceSampleMapper sampleMapper;
    @Mock
    private PriceEstimateConfigMapper configMapper;
    @Mock
    private PriceEstimateAuditMapper auditMapper;
    @Mock
    private ItemMapper itemMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MetricsRegistry metrics;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock
    private IdlefishProperties idlefishProperties;

    @InjectMocks
    private PricingService pricingService;

    /** 无 Spring 上下文时初始化 MyBatis-Plus 实体元数据（lambda 缓存），否则 LambdaQueryWrapper 解析失败。 */
    @BeforeAll
    static void initMpMetadata() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "dummy");
        TableInfoHelper.initTableInfo(assistant, PriceSample.class);
        TableInfoHelper.initTableInfo(assistant, PriceEstimateConfig.class);
        TableInfoHelper.initTableInfo(assistant, PriceEstimateAudit.class);
    }

    private static List<PriceSample> richSamples() {
        long[] prices = {100L, 120L, 130L, 140L, 150L, 160L, 200L};
        List<PriceSample> list = new ArrayList<>();
        for (long p : prices) {
            PriceSample s = new PriceSample();
            s.setDealPriceFen(p);
            list.add(s);
        }
        return list;
    }

    private void stubPricingProps() {
        IdlefishProperties.Pricing pricing = new IdlefishProperties.Pricing();
        IdlefishProperties.Notify notify = new IdlefishProperties.Notify();
        notify.setAlertAdminUserId(1L);
        when(idlefishProperties.getPricing()).thenReturn(pricing);
        when(idlefishProperties.getNotify()).thenReturn(notify);
    }

    // ===================== U01 规则兜底数据源 =====================

    @Test
    void basePriceFen_fromConfigMultiplier() {
        PriceEstimateConfig cfg = new PriceEstimateConfig();
        cfg.setBasePriceFen(100000L);
        cfg.setMultiplier(new BigDecimal("1.2"));
        cfg.setStatus(PricingService.STATUS_ENABLED);
        when(configMapper.selectOne(any())).thenReturn(cfg);
        java.util.Optional<Long> opt = pricingService.basePriceFen(10L, 3);
        assertTrue(opt.isPresent());
        assertEquals(120000L, opt.get());
    }

    @Test
    void basePriceFen_noData_empty() {
        when(configMapper.selectOne(any())).thenReturn(null);
        when(sampleMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertTrue(pricingService.basePriceFen(10L, 3).isEmpty());
    }

    // ===================== 估价三路径 =====================

    @Test
    void estimate_samplePath() {
        when(sampleMapper.selectList(any())).thenReturn(richSamples());
        PriceEstimateVO vo = pricingService.estimate(10L, 2);
        assertTrue(vo.isHasData());
        assertEquals(PriceEstimator.SOURCE_SAMPLE, vo.getSource());
        assertEquals(7, vo.getSampleCount());
        assertEquals(125L, vo.getMinFen());
        assertEquals(140L, vo.getMedianFen());
        assertEquals(155L, vo.getMaxFen());
    }

    @Test
    void estimate_ruleFallbackPath() {
        when(sampleMapper.selectList(any())).thenReturn(Arrays.asList(new PriceSample())); // 不足
        PriceEstimateConfig cfg = new PriceEstimateConfig();
        cfg.setBasePriceFen(100000L);
        cfg.setStatus(PricingService.STATUS_ENABLED);
        when(configMapper.selectOne(any())).thenReturn(cfg);
        when(sampleMapper.selectList(any())).thenReturn(Arrays.asList(new PriceSample())); // 品类中位数查询也为空
        PriceEstimateVO vo = pricingService.estimate(10L, 1);
        assertTrue(vo.isHasData());
        assertEquals(PriceEstimator.SOURCE_RULE, vo.getSource());
        assertEquals(90000L, vo.getMinFen()); // 100000±10%
        assertEquals(110000L, vo.getMaxFen());
    }

    @Test
    void estimate_noDataPath() {
        when(sampleMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(configMapper.selectOne(any())).thenReturn(null);
        PriceEstimateVO vo = pricingService.estimate(10L, 3);
        assertFalse(vo.isHasData());
    }

    // ===================== 偏离判定 REQ-09 =====================

    @Test
    void deviation_low() {
        when(sampleMapper.selectList(any())).thenReturn(richSamples());
        PriceDeviationVO vo = pricingService.deviation(10L, 2, 40L);
        assertTrue(vo.isHasData());
        assertEquals(PriceEstimator.DEVIATION_LOW, vo.getDeviation());
    }

    // ===================== 成交价回流 REQ-05 =====================

    @Test
    void recordDeal_insertsSample() {
        Order order = new Order();
        order.setItemId(99L);
        PayOrder po = new PayOrder();
        po.setAmount(12345L);
        Item item = new Item();
        item.setCategoryId(10L);
        item.setConditionLevel(3);
        when(itemMapper.selectById(99L)).thenReturn(item);

        pricingService.recordDeal(order, po);

        ArgumentCaptor<PriceSample> cap = ArgumentCaptor.forClass(PriceSample.class);
        verify(sampleMapper).insert(cap.capture());
        assertEquals(12345L, cap.getValue().getDealPriceFen());
        assertEquals(10L, cap.getValue().getCategoryId());
        assertEquals(3, cap.getValue().getConditionLevel());
    }

    @Test
    void recordDeal_safeOnNull() {
        pricingService.recordDeal(null, null);
        pricingService.recordDeal(new Order(), null);
        verify(sampleMapper, never()).insert(any(PriceSample.class));
    }

    // ===================== 异常低价送审 REQ-10 =====================

    @Test
    void evaluateListing_abnormal_triggersAuditAndNotify() {
        stubPricingProps();
        when(sampleMapper.selectList(any())).thenReturn(richSamples()); // min=130
        Item item = new Item();
        item.setId(77L);
        item.setCategoryId(10L);
        item.setConditionLevel(2);
        item.setPrice(40L); // 40 < 130*0.5=65 → 异常

        pricingService.evaluateListing(item);

        verify(auditMapper).insert(any(PriceEstimateAudit.class));
        verify(notificationService).notify(eq(1L), eq(NotificationType.SYSTEM_ALERT),
                any(String.class), any(String.class), any(String.class));
    }

    @Test
    void evaluateListing_normal_noAudit() {
        stubPricingProps();
        when(sampleMapper.selectList(any())).thenReturn(richSamples());
        Item item = new Item();
        item.setId(77L);
        item.setCategoryId(10L);
        item.setConditionLevel(2);
        item.setPrice(150L); // 150 >= 65 → 正常

        pricingService.evaluateListing(item);

        verify(auditMapper, never()).insert(any(PriceEstimateAudit.class));
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    // ===================== 运营系数覆盖 REQ-08 =====================

    @Test
    void override_existingConfig_updatesAndAudits() {
        PriceEstimateConfig cfg = new PriceEstimateConfig();
        cfg.setId(5L);
        cfg.setBasePriceFen(50000L);
        when(configMapper.selectOne(any())).thenReturn(cfg);
        when(configMapper.updateById(any(PriceEstimateConfig.class))).thenReturn(1);

        pricingService.override(10L, new BigDecimal("1.3"), "大促折价", 2L);

        verify(configMapper).updateById(any(PriceEstimateConfig.class));
        verify(auditMapper).insert(any(PriceEstimateAudit.class));
    }

    @Test
    void override_noConfig_throws() {
        when(configMapper.selectOne(any())).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> pricingService.override(10L, new BigDecimal("1.3"), "x", 2L));
        assertEquals(Code.PRICING_CONFIG_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void override_invalidMultiplier_throws() {
        assertThrows(BizException.class,
                () -> pricingService.override(10L, new BigDecimal("-1"), "x", 2L));
    }
}
