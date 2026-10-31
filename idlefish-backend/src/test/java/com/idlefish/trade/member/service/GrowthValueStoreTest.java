package com.idlefish.trade.member.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.member.entity.GrowthValueLog;
import com.idlefish.trade.member.entity.MemberLevel;
import com.idlefish.trade.member.entity.UserGrowth;
import com.idlefish.trade.member.mapper.GrowthValueLogMapper;
import com.idlefish.trade.member.mapper.MemberLevelMapper;
import com.idlefish.trade.member.mapper.UserGrowthMapper;
import com.idlefish.trade.risk.entity.DeviceFingerprint;
import com.idlefish.trade.risk.mapper.DeviceFingerprintMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成长值数据访问协作类（GrowthValueStore）离线单测（Mockito，无 Spring / 无 MySQL）：
 * 锁定账户惰性创建、CAS 累计、幂等预检、流水落库与等级配置排序等外提逻辑与重构前一致。
 */
@ExtendWith(MockitoExtension.class)
class GrowthValueStoreTest {

    @Mock private UserGrowthMapper userGrowthMapper;
    @Mock private GrowthValueLogMapper growthValueLogMapper;
    @Mock private MemberLevelMapper memberLevelMapper;
    @Mock private DeviceFingerprintMapper deviceFingerprintMapper;

    private IdlefishProperties.Growth growth;
    private GrowthValueStore store;

    @BeforeAll
    static void initMpMetadata() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "dummy");
        TableInfoHelper.initTableInfo(assistant, UserGrowth.class);
        TableInfoHelper.initTableInfo(assistant, GrowthValueLog.class);
        TableInfoHelper.initTableInfo(assistant, MemberLevel.class);
        TableInfoHelper.initTableInfo(assistant, DeviceFingerprint.class);
    }

    @BeforeEach
    void setUp() {
        growth = new IdlefishProperties.Growth();
        store = new GrowthValueStore(userGrowthMapper, growthValueLogMapper, memberLevelMapper,
                deviceFingerprintMapper, growth);
    }

    @Test
    void ensure_returnsExistingWithoutInsert() {
        UserGrowth existing = new UserGrowth();
        existing.setId(10L);
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        UserGrowth r = store.ensure(1L);
        assertEquals(existing, r);
        verify(userGrowthMapper, never()).insert(any(UserGrowth.class));
    }

    @Test
    void ensure_createsDefaultAccountWhenAbsent() {
        when(userGrowthMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(userGrowthMapper.insert(any(UserGrowth.class))).thenReturn(1);
        UserGrowth r = store.ensure(1L);
        assertNotNull(r);
        assertEquals(GrowthValueStore.DEFAULT_LEVEL, r.getLevelCode());
        assertEquals(0, r.getGrowthValue());
        verify(userGrowthMapper).insert(any(UserGrowth.class));
    }

    @Test
    void alreadyLogged_reflectsCount() {
        when(growthValueLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertTrue(store.alreadyLogged(1L, "EARN", "biz"));
        when(growthValueLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertFalse(store.alreadyLogged(1L, "EARN", "biz"));
        assertFalse(store.alreadyLogged(1L, "EARN", null));
    }

    @Test
    void writeLog_insertsSanitizedRecord() {
        store.writeLog(1L, "EARN", "biz", 5, 0, 5, null, "remark");
        ArgumentCaptor<GrowthValueLog> cap = ArgumentCaptor.forClass(GrowthValueLog.class);
        verify(growthValueLogMapper).insert(cap.capture());
        GrowthValueLog l = cap.getValue();
        assertEquals(5, l.getDelta());
        assertEquals(0, l.getBeforeValue());
        assertEquals(5, l.getAfterValue());
        assertEquals("v1", l.getRuleVersion());
        assertEquals(0, l.getFrozen());
        assertEquals("remark", l.getRemark());
    }

    @Test
    void casUpdate_delegatesToMapper() {
        when(userGrowthMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        assertTrue(store.casUpdate(10L, 0, 5, 5));
    }

    @Test
    void tiers_sortedByMinGrowthAscending() {
        MemberLevel l2 = new MemberLevel();
        l2.setLevelCode("L2");
        l2.setMinGrowth(100);
        MemberLevel l1 = new MemberLevel();
        l1.setLevelCode("L1");
        l1.setMinGrowth(0);
        when(memberLevelMapper.selectList(null)).thenReturn(new java.util.ArrayList<>(List.of(l2, l1)));
        List<MemberLevel> t = store.tiers();
        assertEquals("L1", t.get(0).getLevelCode());
        assertEquals("L2", t.get(1).getLevelCode());
    }

    @Test
    void buildAntiFraudInput_queriesLinkedAndEarn() {
        when(deviceFingerprintMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(growthValueLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertNotNull(store.buildAntiFraudInput(1L, 1000L));
        verify(deviceFingerprintMapper).selectList(any(LambdaQueryWrapper.class));
        verify(growthValueLogMapper).selectCount(any(LambdaQueryWrapper.class));
    }

    @Test
    void decayCas_delegatesToMapper() {
        when(userGrowthMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);
        assertTrue(store.decayCas(10L, 300, 250));
    }
}
