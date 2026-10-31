package com.idlefish.trade.marketing.activity.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.idlefish.trade.marketing.activity.entity.ActivityRecord;
import com.idlefish.trade.marketing.activity.entity.ActivityReconciliation;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.marketing.activity.mapper.ActivityRecordMapper;
import com.idlefish.trade.marketing.activity.mapper.ActivityReconciliationMapper;
import com.idlefish.trade.marketing.activity.mapper.MarketingActivityMapper;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动内核数据访问单测（ITER-F08）：Mockito 离线，验证 ActivityStore 对 4 个 Mapper / 用户 / 缓存的读写与 CAS 推进正确性。
 * 无 Spring 上下文需初始化 MyBatis-Plus 实体元数据，否则 Lambda 包装器解析失败。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityStoreTest {

    @Mock
    private MarketingActivityMapper activityMapper;
    @Mock
    private ActivityRecordMapper recordMapper;
    @Mock
    private ActivityReconciliationMapper reconMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private ActivityStore store;

    @BeforeAll
    static void initMpMetadata() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "dummy");
        TableInfoHelper.initTableInfo(assistant, MarketingActivity.class);
        TableInfoHelper.initTableInfo(assistant, ActivityRecord.class);
        TableInfoHelper.initTableInfo(assistant, ActivityReconciliation.class);
    }

    @BeforeEach
    void init() {
        store = new ActivityStore(activityMapper, recordMapper, reconMapper, userMapper, redisTemplate);
    }

    // ===================== 活动读 =====================

    @Test
    void selectActivityById_delegates() {
        MarketingActivity a = new MarketingActivity();
        when(activityMapper.selectById(1L)).thenReturn(a);
        assertEquals(a, store.selectActivityById(1L));
        verify(activityMapper).selectById(1L);
    }

    @Test
    void selectOngoingActivities_filtersKind() {
        List<MarketingActivity> list = new ArrayList<>();
        when(activityMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<MarketingActivity>>any())).thenReturn(list);
        assertEquals(list, store.selectOngoingActivities("LOTTERY"));
        verify(activityMapper).selectList(ArgumentMatchers.<LambdaQueryWrapper<MarketingActivity>>any());
    }

    @Test
    void selectAll_delegates() {
        List<MarketingActivity> list = new ArrayList<>();
        when(activityMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<MarketingActivity>>any())).thenReturn(list);
        assertEquals(list, store.selectAll());
    }

    // ===================== 库存 / 补贴 CAS =====================

    @Test
    void decrementStock_returnsRows() {
        when(activityMapper.update((MarketingActivity) any(), any())).thenReturn(1);
        assertEquals(1, store.decrementStock(1L, 2));
        verify(activityMapper).update((MarketingActivity) any(), any());
    }

    @Test
    void rollbackStock_returnsRows() {
        when(activityMapper.update((MarketingActivity) any(), any())).thenReturn(1);
        assertEquals(1, store.rollbackStock(1L, 2));
    }

    @Test
    void addSubsidyUnbounded_returnsRows() {
        when(activityMapper.update((MarketingActivity) any(), any())).thenReturn(1);
        assertEquals(1, store.addSubsidyUnbounded(1L, 50L));
    }

    @Test
    void addSubsidyBounded_returnsRows() {
        when(activityMapper.update((MarketingActivity) any(), any())).thenReturn(1);
        assertEquals(1, store.addSubsidyBounded(1L, 50L));
    }

    @Test
    void markEnded_returnsRows() {
        when(activityMapper.update((MarketingActivity) any(), any())).thenReturn(1);
        assertEquals(1, store.markEnded(1L));
    }

    @Test
    void updateActivityStatus_usesUpdateById() {
        when(activityMapper.updateById((MarketingActivity) any())).thenReturn(1);
        assertEquals(1, store.updateActivityStatus(1L, "ONGOING"));
        verify(activityMapper).updateById((MarketingActivity) any());
    }

    // ===================== 参与记录 =====================

    @Test
    void countUserJoined_returnsInt_nullSafe() {
        when(recordMapper.selectCount(ArgumentMatchers.<LambdaQueryWrapper<ActivityRecord>>any())).thenReturn(3L);
        assertEquals(3, store.countUserJoined(1L, 9L));
        when(recordMapper.selectCount(ArgumentMatchers.<LambdaQueryWrapper<ActivityRecord>>any())).thenReturn((Long) null);
        assertEquals(0, store.countUserJoined(1L, 9L));
    }

    @Test
    void selectByIdempotencyKey_delegates() {
        ActivityRecord r = new ActivityRecord();
        when(recordMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<ActivityRecord>>any())).thenReturn(r);
        assertEquals(r, store.selectByIdempotencyKey(1L, "idem"));
    }

    @Test
    void insertRecord_delegatesToMapper() {
        ActivityRecord r = new ActivityRecord();
        store.insertRecord(r);
        verify(recordMapper).insert((ActivityRecord) any());
    }

    @Test
    void selectByActivityId_delegates() {
        List<ActivityRecord> list = new ArrayList<>();
        when(recordMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<ActivityRecord>>any())).thenReturn(list);
        assertEquals(list, store.selectByActivityId(1L));
    }

    @Test
    void selectByUser_delegates() {
        List<ActivityRecord> list = new ArrayList<>();
        when(recordMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<ActivityRecord>>any())).thenReturn(list);
        assertEquals(list, store.selectByUser(9L));
    }

    @Test
    void countByUserSince_returnsLong_nullSafe() {
        when(recordMapper.selectCount(ArgumentMatchers.<LambdaQueryWrapper<ActivityRecord>>any())).thenReturn(7L);
        assertEquals(7L, store.countByUserSince(LocalDateTime.now()));
        when(recordMapper.selectCount(ArgumentMatchers.<LambdaQueryWrapper<ActivityRecord>>any())).thenReturn((Long) null);
        assertEquals(0L, store.countByUserSince(LocalDateTime.now()));
    }

    // ===================== 对账 =====================

    @Test
    void selectReconByActivityId_delegates() {
        ActivityReconciliation r = new ActivityReconciliation();
        when(reconMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<ActivityReconciliation>>any())).thenReturn(r);
        assertEquals(r, store.selectReconByActivityId(1L));
    }

    @Test
    void insertRecon_delegatesToMapper() {
        ActivityReconciliation r = new ActivityReconciliation();
        store.insertRecon(r);
        verify(reconMapper).insert((ActivityReconciliation) any());
    }

    @Test
    void updateReconById_delegatesToMapper() {
        ActivityReconciliation r = new ActivityReconciliation();
        store.updateReconById(r);
        verify(reconMapper).updateById((ActivityReconciliation) any());
    }

    // ===================== 用户 / 缓存 =====================

    @Test
    void selectUserById_delegates() {
        User u = new User();
        when(userMapper.selectById(9L)).thenReturn(u);
        assertEquals(u, store.selectUserById(9L));
        verify(userMapper).selectById(9L);
    }

    @Test
    void preheatCache_writesWhenStockPresent() {
        MarketingActivity a = new MarketingActivity();
        a.setStock(80);
        when(activityMapper.selectById(1L)).thenReturn(a);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        doNothing().when(ops).set(anyString(), any());
        store.preheatCache(1L);
        verify(redisTemplate).opsForValue();
        verify(ops).set(anyString(), any());
    }

    @Test
    void preheatCache_noCacheWhenActivityNull() {
        when(activityMapper.selectById(1L)).thenReturn(null);
        store.preheatCache(1L);
        verify(redisTemplate, never()).opsForValue();
    }
}
