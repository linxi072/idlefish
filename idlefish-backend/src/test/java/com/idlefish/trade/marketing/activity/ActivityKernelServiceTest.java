package com.idlefish.trade.marketing.activity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.marketing.activity.entity.ActivityRecord;
import com.idlefish.trade.marketing.activity.service.ActivityKernelService;
import com.idlefish.trade.marketing.activity.entity.ActivityReconciliation;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.marketing.activity.mapper.ActivityRecordMapper;
import com.idlefish.trade.marketing.activity.mapper.ActivityReconciliationMapper;
import com.idlefish.trade.marketing.activity.mapper.MarketingActivityMapper;
import com.idlefish.trade.marketing.activity.vo.MarketingActivityVO;
import com.idlefish.trade.marketing.activity.vo.ActivityReconcileVO;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统一活动内核服务单测（ITER-F08 / REQ-01、REQ-03、REQ-05、REQ-06、REQ-11、REQ-13、REQ-15）。
 * 纯 Mockito：所有 Mapper / 缓存 / 指标均为 mock，DB 不落地；覆盖参与全链路、风控、幂等、
 * 库存网关、补贴预算闸门、对账与发布。
 * <p>
 * 离线测 MP Lambda 包装器需先初始化实体 TableInfo（否则 lambda 列解析抛异常）。
 */
@ExtendWith(MockitoExtension.class)
class ActivityKernelServiceTest {

    @Mock
    private MarketingActivityMapper activityMapper;
    @Mock
    private ActivityRecordMapper recordMapper;
    @Mock
    private ActivityReconciliationMapper reconMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ActivityRiskGate riskGate;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private MetricsRegistry metrics;

    @InjectMocks
    private ActivityKernelService kernel;

    @BeforeAll
    static void initMpLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "dummy");
        TableInfoHelper.initTableInfo(assistant, MarketingActivity.class);
        TableInfoHelper.initTableInfo(assistant, ActivityRecord.class);
        TableInfoHelper.initTableInfo(assistant, ActivityReconciliation.class);
    }

    private MarketingActivity activity(String status) {
        MarketingActivity a = new MarketingActivity();
        a.setId(1L);
        a.setKind("LOTTERY");
        a.setTitle("测试活动");
        a.setStatus(status);
        a.setStock(100);
        a.setSoldCount(0);
        a.setLimitPerUser(0);
        a.setAudienceType("ALL");
        a.setLossFloorFen(0L);
        a.setSubsidyBudgetFen(null);
        a.setStartAt(LocalDateTime.now().minusDays(1));
        a.setEndAt(LocalDateTime.now().plusDays(1));
        return a;
    }

    private User user() {
        User u = new User();
        u.setId(9L);
        return u;
    }

    private void stubRedis() {
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        doNothing().when(ops).set(anyString(), any());
    }

    // ===== participate 主链路 =====

    @Test
    void participate_success() {
        when(activityMapper.selectById(1L)).thenReturn(activity("ONGOING"));
        when(userMapper.selectById(9L)).thenReturn(user());
        when(riskGate.allow(anyLong(), any(), anyLong(), anyString())).thenReturn(true);
        when(recordMapper.selectOne(any())).thenReturn(null);
        when(activityMapper.update(any(), any())).thenReturn(1);
        when(recordMapper.insert(any(ActivityRecord.class))).thenReturn(1);
        doNothing().when(metrics).increment(anyString());

        ActivityRecord rec = kernel.participate(9L, "dev", 1L, "idem-1", "biz-1", 1, 1000L, 0L,
                null, "JOINED", "{}");

        assertNotNull(rec);
        assertEquals("JOINED", rec.getStatus());
        assertEquals(1000L, rec.getAmountFen());
        verify(activityMapper).update(any(), any());
        verify(recordMapper).insert(any(ActivityRecord.class));
        verify(metrics).increment("activity.join.success");
    }

    @Test
    void participate_riskBlocked() {
        when(activityMapper.selectById(1L)).thenReturn(activity("ONGOING"));
        when(userMapper.selectById(9L)).thenReturn(user());
        when(riskGate.allow(anyLong(), any(), anyLong(), anyString())).thenReturn(false);
        doNothing().when(metrics).increment(anyString());

        BizException ex = assertThrows(BizException.class,
                () -> kernel.participate(9L, "dev", 1L, "idem-2", "biz-2", 1, 0L, 0L, null, "JOINED", "{}"));
        assertEquals(Code.ACTIVITY_RISK_BLOCKED.getCode(), ex.getCode());
        verify(metrics).increment("activity.risk.blocked");
    }

    @Test
    void participate_idempotent_replay() {
        ActivityRecord existing = new ActivityRecord();
        existing.setId(99L);
        existing.setStatus("JOINED");
        when(activityMapper.selectById(1L)).thenReturn(activity("ONGOING"));
        when(userMapper.selectById(9L)).thenReturn(user());
        when(riskGate.allow(anyLong(), any(), anyLong(), anyString())).thenReturn(true);
        when(recordMapper.selectOne(any())).thenReturn(existing);

        ActivityRecord rec = kernel.participate(9L, "dev", 1L, "idem-3", "biz-3", 1, 0L, 0L, null, "JOINED", "{}");
        assertEquals(existing, rec);
        verify(activityMapper, never()).update(any(), any());
    }

    @Test
    void participate_stockNotEnough() {
        when(activityMapper.selectById(1L)).thenReturn(activity("ONGOING"));
        when(userMapper.selectById(9L)).thenReturn(user());
        when(riskGate.allow(anyLong(), any(), anyLong(), anyString())).thenReturn(true);
        when(recordMapper.selectOne(any())).thenReturn(null);
        when(activityMapper.update(any(), any())).thenReturn(0); // 库存扣减失败
        doNothing().when(metrics).increment(anyString());

        BizException ex = assertThrows(BizException.class,
                () -> kernel.participate(9L, "dev", 1L, "idem-4", "biz-4", 1, 0L, 0L, null, "JOINED", "{}"));
        assertEquals(Code.STOCK_NOT_ENOUGH.getCode(), ex.getCode());
        verify(metrics).increment("activity.stock.preempted");
    }

    @Test
    void participate_subsidyBudgetExceeded() {
        MarketingActivity a = activity("ONGOING");
        a.setSubsidyBudgetFen(100L);
        when(activityMapper.selectById(1L)).thenReturn(a);
        when(userMapper.selectById(9L)).thenReturn(user());
        when(riskGate.allow(anyLong(), any(), anyLong(), anyString())).thenReturn(true);
        when(recordMapper.selectOne(any())).thenReturn(null);
        // 第一次 update=库存扣减成功(1)；第二次 update=补贴累加超额失败(0)
        when(activityMapper.update(any(), any())).thenReturn(1, 0);
        when(recordMapper.insert(any(ActivityRecord.class))).thenReturn(1);
        doNothing().when(metrics).increment(anyString());

        BizException ex = assertThrows(BizException.class,
                () -> kernel.participate(9L, "dev", 1L, "idem-5", "biz-5", 1, 0L, 200L, null, "JOINED", "{}"));
        assertEquals(Code.SUBSIDY_BUDGET_EXCEEDED.getCode(), ex.getCode());
        verify(metrics).increment("activity.subsidy.budget_exceeded");
    }

    @Test
    void participate_duplicateKey_rollbackStock() {
        when(activityMapper.selectById(1L)).thenReturn(activity("ONGOING"));
        when(userMapper.selectById(9L)).thenReturn(user());
        when(riskGate.allow(anyLong(), any(), anyLong(), anyString())).thenReturn(true);
        when(recordMapper.selectOne(any())).thenReturn(null, new ActivityRecord()); // 首次查重 null；DUP 回滚后查重返回既有
        when(activityMapper.update(any(), any())).thenReturn(1);
        when(recordMapper.insert(any(ActivityRecord.class))).thenThrow(new DuplicateKeyException("dup"));

        // 并发重放：insert 抛 DUP，回滚库存后返回既有记录（不抛异常）
        ActivityRecord rec = kernel.participate(9L, "dev", 1L, "idem-6", "biz-6", 1, 0L, 0L, null, "JOINED", "{}");
        assertNotNull(rec);
    }

    // ===== 统计 / VO =====

    @Test
    void countUserJoined_returnsCount() {
        when(recordMapper.selectCount(any())).thenReturn(3L);
        assertEquals(3, kernel.countUserJoined(1L, 9L));
    }

    @Test
    void getActivity_joinedFlag() {
        when(activityMapper.selectById(1L)).thenReturn(activity("ONGOING"));
        when(recordMapper.selectCount(any())).thenReturn(1L); // 已参与
        MarketingActivityVO vo = kernel.getActivity(1L, 9L);
        assertTrue(vo.getJoined());
    }

    // ===== 对账（REQ-11）=====

    @Test
    void reconcile_basic() {
        MarketingActivity a = activity("ONGOING");
        a.setStock(50);
        a.setLossFloorFen(0L);
        when(activityMapper.selectById(1L)).thenReturn(a);
        ActivityRecord r = new ActivityRecord();
        r.setStatus("WON");
        r.setAmountFen(1000L);
        r.setSubsidyFen(0L);
        List<ActivityRecord> records = new ArrayList<>();
        records.add(r);
        when(recordMapper.selectList(any())).thenReturn(records);
        when(activityMapper.update(any(), any())).thenReturn(1);
        when(reconMapper.selectOne(any())).thenReturn(null);
        when(reconMapper.insert(any(ActivityReconciliation.class))).thenReturn(1);

        ActivityReconciliation recon = kernel.reconcile(1L);
        assertEquals(1, recon.getStockSold());
        assertEquals(50, recon.getStockRecycled()); // 剩余库存回收展示
        assertEquals(0, recon.getSubsidyTotalFen());
        assertEquals(0L, recon.getLossFen());
        verify(reconMapper).insert(any(ActivityReconciliation.class));
    }

    // ===== 发布（REQ-02）=====

    @Test
    void publish_fromDraft_toOngoing() {
        stubRedis();
        when(activityMapper.selectById(1L)).thenReturn(activity("DRAFT"));
        ArgumentCaptor<MarketingActivity> captor = ArgumentCaptor.forClass(MarketingActivity.class);
        when(activityMapper.updateById(captor.capture())).thenReturn(1);
        doNothing().when(metrics).increment(anyString());

        kernel.publish(1L);
        assertEquals("ONGOING", captor.getValue().getStatus());
        verify(metrics).increment("activity.publish");
    }

    @Test
    void publish_notDraft_throws() {
        when(activityMapper.selectById(1L)).thenReturn(activity("ONGOING"));
        BizException ex = assertThrows(BizException.class, () -> kernel.publish(1L));
        assertEquals(Code.STATE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    // ===== 预热（REQ-02）=====

    @Test
    void preheat_writesCache() {
        stubRedis();
        MarketingActivity a = activity("ONGOING");
        a.setStock(80);
        when(activityMapper.selectById(1L)).thenReturn(a);
        kernel.preheat(1L);
        verify(redisTemplate.opsForValue()).set(anyString(), any());
    }

    @Test
    void preheat_nullActivity_noCache() {
        when(activityMapper.selectById(1L)).thenReturn(null);
        kernel.preheat(1L);
        verify(redisTemplate, never()).opsForValue();
    }
}
