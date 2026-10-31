package com.idlefish.trade.pricing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.pricing.entity.PriceEstimateAudit;
import com.idlefish.trade.pricing.entity.PriceEstimateConfig;
import com.idlefish.trade.pricing.entity.PriceSample;
import com.idlefish.trade.pricing.mapper.PriceEstimateAuditMapper;
import com.idlefish.trade.pricing.mapper.PriceEstimateConfigMapper;
import com.idlefish.trade.pricing.mapper.PriceSampleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 估价数据访问单测（REQ-07）：Mockito 离线，验证 PricingStore 对 4 个 Mapper 的读/写与编排正确性。
 * 无 Spring 上下文需初始化 MyBatis-Plus 实体元数据，否则 LambdaQueryWrapper 解析失败。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingStoreTest {

    @Mock
    private PriceSampleMapper sampleMapper;
    @Mock
    private PriceEstimateConfigMapper configMapper;
    @Mock
    private PriceEstimateAuditMapper auditMapper;
    @Mock
    private ItemMapper itemMapper;

    private PricingStore store;

    @BeforeAll
    static void initMpMetadata() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "dummy");
        TableInfoHelper.initTableInfo(assistant, PriceSample.class);
        TableInfoHelper.initTableInfo(assistant, PriceEstimateConfig.class);
        TableInfoHelper.initTableInfo(assistant, PriceEstimateAudit.class);
    }

    @BeforeEach
    void init() {
        store = new PricingStore(sampleMapper, configMapper, auditMapper, itemMapper);
    }

    private static List<PriceSample> samples(Long... prices) {
        List<PriceSample> list = new ArrayList<>();
        for (Long p : prices) {
            PriceSample s = new PriceSample();
            s.setDealPriceFen(p);
            s.setCategoryId(10L);
            s.setConditionLevel(3);
            list.add(s);
        }
        return list;
    }

    // ===================== 读 =====================

    @Test
    void config_returnsSelectOne() {
        PriceEstimateConfig cfg = new PriceEstimateConfig();
        cfg.setId(9L);
        when(configMapper.selectOne(any())).thenReturn(cfg);
        assertEquals(9L, store.config(10L).getId());
        assertNull(store.config(null));
    }

    @Test
    void categoryMedian_computesPercentile() {
        when(sampleMapper.selectList(any())).thenReturn(samples(100L, 200L, 300L, 400L, 500L, 600L, 700L));
        assertEquals(400L, store.categoryMedian(10L));
    }

    @Test
    void categoryMedian_nullWhenEmpty() {
        when(sampleMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertNull(store.categoryMedian(10L));
    }

    @Test
    void categoryMedian_nullWhenAllInvalid() {
        when(sampleMapper.selectList(any())).thenReturn(samples(null, 0L, null));
        assertNull(store.categoryMedian(10L));
    }

    @Test
    void rawBaseline_fallsToMedianWhenCfgAbsent() {
        // cfg=null → 取品类中位数
        when(sampleMapper.selectList(any())).thenReturn(samples(100L, 500L, 900L));
        // 中位数 = 500
        assertEquals(500L, store.rawBaseline(10L, null));
    }

    @Test
    void sameConditionSamples_filtersInvalid() {
        List<PriceSample> mixed = new ArrayList<>();
        PriceSample a = new PriceSample();
        a.setDealPriceFen(null);
        PriceSample b = new PriceSample();
        b.setDealPriceFen(0L);
        PriceSample c = new PriceSample();
        c.setDealPriceFen(100L);
        PriceSample d = new PriceSample();
        d.setDealPriceFen(200L);
        mixed.add(a);
        mixed.add(b);
        mixed.add(c);
        mixed.add(d);
        when(sampleMapper.selectList(any())).thenReturn(mixed);
        List<Long> out = store.sameConditionSamples(10L, 3);
        assertEquals(2, out.size());
        assertTrue(out.contains(100L));
        assertTrue(out.contains(200L));
    }

    @Test
    void selectItemById_delegatesToItemMapper() {
        Item item = new Item();
        item.setId(42L);
        when(itemMapper.selectById(42L)).thenReturn(item);
        assertEquals(42L, store.selectItemById(42L).getId());
        assertNull(store.selectItemById(null));
    }

    @Test
    void insertSample_delegatesToSampleMapper() {
        PriceSample s = new PriceSample();
        s.setDealPriceFen(123L);
        store.insertSample(s);
        verify(sampleMapper).insert(s);
    }

    @Test
    void allCategoryIds_unionOfSampleAndConfig() {
        PriceSample s = new PriceSample();
        s.setCategoryId(10L);
        when(sampleMapper.selectList(any())).thenReturn(Arrays.asList(s));
        PriceEstimateConfig c = new PriceEstimateConfig();
        c.setCategoryId(20L);
        when(configMapper.selectList(any())).thenReturn(Arrays.asList(c));
        Set<Long> ids = store.allCategoryIds();
        assertEquals(new HashSet<>(Arrays.asList(10L, 20L)), ids);
    }

    @Test
    void abnormalList_filtersByBizType() {
        PriceEstimateAudit a = new PriceEstimateAudit();
        a.setId(1L);
        when(auditMapper.selectList(any())).thenReturn(Arrays.asList(a));
        List<PriceEstimateAudit> list = store.abnormalList();
        assertEquals(1, list.size());
        verify(auditMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void auditLog_ordersByCreatedAt() {
        PriceEstimateAudit a = new PriceEstimateAudit();
        a.setId(2L);
        when(auditMapper.selectList(any())).thenReturn(Arrays.asList(a));
        assertEquals(2L, store.auditLog().get(0).getId());
    }

    // ===================== 写 =====================

    @Test
    void writeAudit_buildsAndInserts() {
        store.writeAudit(10L, PricingService.BIZ_RECALC, 5L, "before", "after", 0L, "reason");
        ArgumentCaptor<PriceEstimateAudit> cap = ArgumentCaptor.forClass(PriceEstimateAudit.class);
        verify(auditMapper).insert(cap.capture());
        PriceEstimateAudit a = cap.getValue();
        assertEquals(10L, a.getCategoryId());
        assertEquals(PricingService.BIZ_RECALC, a.getBizType());
        assertEquals(5L, a.getBizId());
        assertEquals("before", a.getBeforeJson());
        assertEquals("after", a.getAfterJson());
        assertEquals(0L, a.getOperatorId());
        assertEquals("reason", a.getReason());
    }

    @Test
    void saveConfigUpsert_insertsWhenAbsent() {
        PriceEstimateConfig saved = new PriceEstimateConfig();
        saved.setId(7L);
        saved.setCategoryId(10L);
        // 首次 config 查询返回 null（无既有配置），upsert 后回查返回 saved
        when(configMapper.selectOne(any())).thenReturn(null, saved);
        when(configMapper.insert((PriceEstimateConfig) any())).thenReturn(1);
        PriceEstimateConfig after = store.saveConfigUpsert(10L, 100000L, new BigDecimal("1.2"),
                PricingService.STATUS_ENABLED, 1L);
        verify(configMapper).insert((PriceEstimateConfig) any());
        verify(configMapper, never()).updateById((PriceEstimateConfig) any());
        assertEquals(7L, after.getId());
    }

    @Test
    void saveConfigUpsert_updatesWhenPresent() {
        PriceEstimateConfig existing = new PriceEstimateConfig();
        existing.setId(7L);
        PriceEstimateConfig afterCfg = new PriceEstimateConfig();
        afterCfg.setId(7L);
        when(configMapper.selectOne(any())).thenReturn(existing, afterCfg);
        when(configMapper.updateById((PriceEstimateConfig) any())).thenReturn(1);
        store.saveConfigUpsert(10L, 100000L, new BigDecimal("1.2"), PricingService.STATUS_ENABLED, 1L);
        verify(configMapper, never()).insert((PriceEstimateConfig) any());
        verify(configMapper).updateById((PriceEstimateConfig) any());
    }

    @Test
    void overrideConfig_updatesAndReturnsAfter() {
        PriceEstimateConfig existing = new PriceEstimateConfig();
        existing.setId(5L);
        PriceEstimateConfig afterCfg = new PriceEstimateConfig();
        afterCfg.setId(5L);
        when(configMapper.selectOne(any())).thenReturn(existing, afterCfg);
        when(configMapper.updateById((PriceEstimateConfig) any())).thenReturn(1);
        PriceEstimateConfig after = store.overrideConfig(10L, new BigDecimal("1.3"), 2L);
        verify(configMapper).updateById((PriceEstimateConfig) any());
        assertEquals(5L, after.getId());
    }

    @Test
    void overrideConfig_throwsWhenAbsent() {
        when(configMapper.selectOne(any())).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> store.overrideConfig(10L, new BigDecimal("1.3"), 2L));
        assertEquals(Code.PRICING_CONFIG_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void recalcUpsert_insertsWhenAbsent() {
        PriceEstimateConfig saved = new PriceEstimateConfig();
        saved.setId(8L);
        when(configMapper.selectOne(any())).thenReturn(null, saved);
        when(configMapper.insert((PriceEstimateConfig) any())).thenReturn(1);
        store.recalcUpsert(10L, 500L, 0L);
        verify(configMapper).insert((PriceEstimateConfig) any());
        verify(configMapper, never()).updateById((PriceEstimateConfig) any());
    }

    @Test
    void recalcUpsert_updatesWhenPresent() {
        PriceEstimateConfig existing = new PriceEstimateConfig();
        existing.setId(8L);
        PriceEstimateConfig afterCfg = new PriceEstimateConfig();
        afterCfg.setId(8L);
        when(configMapper.selectOne(any())).thenReturn(existing, afterCfg);
        when(configMapper.updateById((PriceEstimateConfig) any())).thenReturn(1);
        store.recalcUpsert(10L, 500L, 0L);
        verify(configMapper, never()).insert((PriceEstimateConfig) any());
        verify(configMapper).updateById((PriceEstimateConfig) any());
    }
}
