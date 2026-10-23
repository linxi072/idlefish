package com.idlefish.trade.event;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.event.mapper.EventOutboxMapper;
import com.idlefish.trade.event.mapper.EventRecordMapper;
import com.idlefish.trade.event.mapper.EventReplayTaskMapper;
import com.idlefish.trade.risk.entity.AuditLog;
import com.idlefish.trade.risk.mapper.AuditLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 事件回放服务单测（ITER-T06 / REQ-13 限流回放 + REQ-08 死信重投）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventReplayServiceTest {

    @Mock private EventReplayTaskMapper replayMapper;
    @Mock private EventRecordMapper recordMapper;
    @Mock private EventOutboxMapper outboxMapper;
    @Mock private EventDispatcher dispatcher;
    @Mock private AuditLogMapper auditLogMapper;

    private IdlefishProperties props;
    private EventReplayService service;

    /** 无 Spring 上下文时初始化 MyBatis-Plus 实体元数据（lambda 缓存），否则 LambdaQueryWrapper 解析失败。 */
    @BeforeAll
    static void initMpMetadata() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "dummy");
        TableInfoHelper.initTableInfo(assistant, EventOutbox.class);
        TableInfoHelper.initTableInfo(assistant, EventRecord.class);
        TableInfoHelper.initTableInfo(assistant, EventReplayTask.class);
    }

    @BeforeEach
    void setUp() {
        props = new IdlefishProperties();
        service = new EventReplayService(replayMapper, recordMapper, outboxMapper,
                dispatcher, auditLogMapper, props);
    }

    private EventRecord record(long id) {
        EventRecord rec = new EventRecord();
        rec.setId(id);
        rec.setEventId("e" + id);
        rec.setEventType(EventType.ORDER_PAID.name());
        rec.setVersion(1);
        rec.setAggregateId("NO" + id);
        rec.setTraceId("t" + id);
        rec.setPayload("{\"amountFen\":100}");
        rec.setOccurredAt(LocalDateTime.now());
        return rec;
    }

    /** 未知事件类型 → EVENT_TYPE_UNSUPPORTED(30046)。 */
    @Test
    void createReplayUnknownTypeRejected() {
        BizException e = assertThrows(BizException.class,
                () -> service.createReplay(1L, "NOT_A_TYPE", LocalDateTime.now().minusDays(1), LocalDateTime.now()));
        assertEquals(Code.EVENT_TYPE_UNSUPPORTED.getCode(), e.getCode());
    }

    /** 非法时间窗 → PARAM_INVALID(10002)。 */
    @Test
    void createReplayInvalidWindowRejected() {
        LocalDateTime now = LocalDateTime.now();
        BizException e = assertThrows(BizException.class,
                () -> service.createReplay(1L, EventType.ORDER_PAID.name(), now, now.minusDays(1)));
        assertEquals(Code.PARAM_INVALID.getCode(), e.getCode());
    }

    /** 命中事件量超上限 → EVENT_REPLAY_LIMIT(30049)。 */
    @Test
    void createReplayOverLimitRejected() {
        when(recordMapper.selectCount(any())).thenReturn((long) (props.getEvent().getReplayMaxEvents() + 1));
        BizException e = assertThrows(BizException.class,
                () -> service.createReplay(1L, EventType.ORDER_PAID.name(),
                        LocalDateTime.now().minusDays(1), LocalDateTime.now()));
        assertEquals(Code.EVENT_REPLAY_LIMIT.getCode(), e.getCode());
        verify(replayMapper, never()).insert(any(EventReplayTask.class));
    }

    /** 正常创建：落任务 + 审计留痕。 */
    @Test
    void createReplayInsertsTaskAndAudit() {
        when(recordMapper.selectCount(any())).thenReturn(5L);
        EventReplayTask task = service.createReplay(1L, EventType.ORDER_PAID.name(),
                LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertEquals("PENDING", task.getStatus());
        assertEquals(0L, task.getCursorId());
        verify(replayMapper).insert(any(EventReplayTask.class));
        verify(auditLogMapper).insert(any(com.idlefish.trade.risk.entity.AuditLog.class));
    }

    /** 批次推进：成功重放 + 副作用跳过计数正确，游标前进；批次满（=batchSize）保持 RUNNING。 */
    @Test
    void advanceBatchCountsAndCursor() {
        props.getEvent().setReplayBatchSize(2); // 恰好批满 → 未耗尽 → RUNNING
        when(recordMapper.selectList(any())).thenReturn(List.of(record(1L), record(2L)));
        when(dispatcher.dispatch(any(EventOutbox.class), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(new EventDispatcher.DispatchResult(true, 1, 1))
                .thenReturn(new EventDispatcher.DispatchResult(false, 1, 0));

        EventReplayTask task = new EventReplayTask();
        task.setReplayNo("EVR1");
        task.setEventType(EventType.ORDER_PAID.name());
        task.setFromTime(LocalDateTime.now().minusDays(1));
        task.setToTime(LocalDateTime.now());
        task.setStatus("PENDING");
        task.setReplayedCount(0);
        task.setSkippedCount(0);
        task.setFailedCount(0);
        task.setCursorId(0L);

        // 批次 < batchSize → 未耗尽，任务保持 RUNNING
        service.advanceBatch(task);
        assertEquals("RUNNING", task.getStatus());
        assertEquals(2L, task.getCursorId());
        assertEquals(1, task.getReplayedCount());
        assertEquals(1, task.getSkippedCount());
        assertEquals(1, task.getFailedCount());
        verify(dispatcher, atLeastOnce()).dispatch(any(EventOutbox.class), org.mockito.ArgumentMatchers.eq(true));
    }

    /** 批次耗尽（返回数 < batchSize）→ 任务 DONE。 */
    @Test
    void advanceBatchCompletesWhenExhausted() {
        when(recordMapper.selectList(any())).thenReturn(List.of(record(1L)));
        when(dispatcher.dispatch(any(EventOutbox.class), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(new EventDispatcher.DispatchResult(true, 1, 0));

        EventReplayTask task = new EventReplayTask();
        task.setReplayNo("EVR2");
        task.setEventType(EventType.ORDER_PAID.name());
        task.setFromTime(LocalDateTime.now().minusDays(1));
        task.setToTime(LocalDateTime.now());
        task.setStatus("RUNNING");
        task.setReplayedCount(0);
        task.setSkippedCount(0);
        task.setFailedCount(0);
        task.setCursorId(0L);

        service.advanceBatch(task);
        assertEquals("DONE", task.getStatus());
        verify(replayMapper).updateById(task);
    }

    /** REQ-08：死信重投——空死信抛 EVENT_DEAD_LETTER_EMPTY(30050)。 */
    @Test
    void redriveEmptyThrows() {
        when(outboxMapper.selectList(any())).thenReturn(List.of());
        BizException e = assertThrows(BizException.class, () -> service.redriveDeadLetters(1L, null));
        assertEquals(Code.EVENT_DEAD_LETTER_EMPTY.getCode(), e.getCode());
    }

    /** REQ-08：死信重投——重置为 PENDING、清零重试计数、记审计。 */
    @Test
    void redriveResetsToPending() {
        EventOutbox dead = new EventOutbox();
        dead.setId(9L);
        dead.setEventId("e9");
        dead.setStatus(EventOutboxStatus.DEAD.name());
        when(outboxMapper.selectList(any())).thenReturn(List.of(dead));
        when(outboxMapper.update(any(), any())).thenReturn(1);

        int n = service.redriveDeadLetters(1L, null);
        assertEquals(1, n);
        verify(auditLogMapper).insert(any(com.idlefish.trade.risk.entity.AuditLog.class));
        assertDoesNotThrow(() -> verify(outboxMapper).update(any(), any()));
    }
}
