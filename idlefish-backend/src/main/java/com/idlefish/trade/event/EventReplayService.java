package com.idlefish.trade.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.TraceContext;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.event.mapper.EventOutboxMapper;
import com.idlefish.trade.event.mapper.EventRecordMapper;
import com.idlefish.trade.event.mapper.EventReplayTaskMapper;
import com.idlefish.trade.risk.entity.AuditLog;
import com.idlefish.trade.risk.mapper.AuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件回放服务（ITER-T06 / REQ-13 限流回放）。
 * <p>
 * 管理员选定事件类型 + 时间窗创建回放任务（校验上限，防误操作全量回放）；
 * 执行由本服务自带的节拍调度推进：<b>每个调度节拍只推进一个批次</b>（增量游标拉取 +
 * {@code replay=true} 分发），批次间隔天然限流，不阻塞其他 @Scheduled 任务
 * （默认单线程调度器下的协作式让步设计）。
 * <ul>
 *   <li>副作用监听器跳过由 {@link EventDispatcher} 按 {@code replay} 标志保证（REQ-13）；</li>
 *   <li>单条事件失败只计数不阻断；游标始终前进，毒丸事件不会卡死任务；</li>
 *   <li>重放经 Outbox 语义分发，消费幂等（REQ-07）保证真实副作用不重复执行。</li>
 * </ul>
 */
@Slf4j
@Service
public class EventReplayService {

    /** 治理端点动作常量（审计留痕）。 */
    static final String ACTION_REPLAY_CREATE = "EVENT_REPLAY_CREATE";
    static final String ACTION_REDRIVE = "EVENT_DEAD_LETTER_REDRIVE";

    private final EventReplayTaskMapper replayMapper;
    private final EventRecordMapper recordMapper;
    private final EventOutboxMapper outboxMapper;
    private final EventDispatcher dispatcher;
    private final AuditLogMapper auditLogMapper;
    private final IdlefishProperties props;

    public EventReplayService(EventReplayTaskMapper replayMapper,
                              EventRecordMapper recordMapper,
                              EventOutboxMapper outboxMapper,
                              EventDispatcher dispatcher,
                              AuditLogMapper auditLogMapper,
                              IdlefishProperties props) {
        this.replayMapper = replayMapper;
        this.recordMapper = recordMapper;
        this.outboxMapper = outboxMapper;
        this.dispatcher = dispatcher;
        this.auditLogMapper = auditLogMapper;
        this.props = props;
    }

    /**
     * 创建回放任务（校验事件类型与时间窗，估算命中事件量并强校验上限）。
     * 任务创建后由节拍调度异步推进；返回任务行供前端轮询进度。
     */
    public EventReplayTask createReplay(long adminId, String eventType,
                                        LocalDateTime fromTime, LocalDateTime toTime) {
        EventType type = EventType.fromCode(eventType);
        if (type == null) {
            throw new BizException(Code.EVENT_TYPE_UNSUPPORTED);
        }
        if (fromTime == null || toTime == null || toTime.isBefore(fromTime)) {
            throw new BizException(Code.PARAM_INVALID, "回放时间窗非法（fromTime 须早于 toTime）");
        }
        // 事件量预检：超过上限直接拒绝，防误操作全量回放冲垮消费侧（REQ-13）
        Long hits = recordMapper.selectCount(new LambdaQueryWrapper<EventRecord>()
                .eq(EventRecord::getEventType, type.name())
                .ge(EventRecord::getOccurredAt, fromTime)
                .le(EventRecord::getOccurredAt, toTime));
        int maxEvents = Math.max(1, props.getEvent().getReplayMaxEvents());
        if (hits != null && hits > maxEvents) {
            throw new BizException(Code.EVENT_REPLAY_LIMIT,
                    "命中事件 " + hits + " 条超过单任务上限 " + maxEvents + "，请缩小时间窗");
        }

        EventReplayTask task = new EventReplayTask();
        task.setReplayNo(IdGenerator.replayNo());
        task.setEventType(type.name());
        task.setFromTime(fromTime);
        task.setToTime(toTime);
        task.setOperatorAdminId(adminId);
        task.setStatus("PENDING");
        task.setReplayedCount(0);
        task.setSkippedCount(0);
        task.setFailedCount(0);
        task.setCursorId(0L);
        replayMapper.insert(task);
        writeAudit(adminId, ACTION_REPLAY_CREATE,
                "replayNo=" + task.getReplayNo() + ", type=" + type + ", hits=" + hits);
        log.info("[event] replay task created, replayNo={}, type={}, window=[{} ~ {}], hits={}",
                task.getReplayNo(), type, fromTime, toTime, hits);
        return task;
    }

    /**
     * 回放节拍调度：每秒推进一个批次（协作式限流）。
     * 单批次 = replay-batch-size 条事件；批次间由调度间隔天然节流（REQ-13）。
     */
    @Scheduled(fixedDelay = 1_000L, initialDelay = 20_000L)
    public void tickReplay() {
        try {
            // 每节拍仅处理最老的一个活动任务，多任务排队不并发
            EventReplayTask task = replayMapper.selectOne(new LambdaQueryWrapper<EventReplayTask>()
                    .in(EventReplayTask::getStatus, "PENDING", "RUNNING")
                    .orderByAsc(EventReplayTask::getId)
                    .last("LIMIT 1"));
            if (task == null) {
                return;
            }
            advanceBatch(task);
        } catch (RuntimeException e) {
            log.error("[event] replay tick failed", e);
        }
    }

    /** 推进一个回放批次：增量游标拉取 → 逐条 replay 分发 → 计数与状态推进。 */
    void advanceBatch(EventReplayTask task) {
        IdlefishProperties.Event cfg = props.getEvent();
        int batchSize = Math.max(1, cfg.getReplayBatchSize());
        if ("PENDING".equals(task.getStatus())) {
            task.setStatus("RUNNING");
        }
        List<EventRecord> batch = recordMapper.selectList(new LambdaQueryWrapper<EventRecord>()
                .eq(EventRecord::getEventType, task.getEventType())
                .ge(EventRecord::getOccurredAt, task.getFromTime())
                .le(EventRecord::getOccurredAt, task.getToTime())
                .gt(EventRecord::getId, task.getCursorId() == null ? 0L : task.getCursorId())
                .orderByAsc(EventRecord::getId)
                .last("LIMIT " + batchSize));

        int replayed = task.getReplayedCount() == null ? 0 : task.getReplayedCount();
        int skipped = task.getSkippedCount() == null ? 0 : task.getSkippedCount();
        int failed = task.getFailedCount() == null ? 0 : task.getFailedCount();
        long cursor = task.getCursorId() == null ? 0L : task.getCursorId();

        for (EventRecord rec : batch) {
            cursor = Math.max(cursor, rec.getId());
            EventDispatcher.DispatchResult result = dispatchReplay(rec, task);
            if (result.isOk()) {
                replayed++;
                skipped += result.getSkipped();
            } else {
                failed++;
            }
        }

        task.setReplayedCount(replayed);
        task.setSkippedCount(skipped);
        task.setFailedCount(failed);
        task.setCursorId(cursor);

        boolean exhausted = batch.size() < batchSize;
        int processed = replayed;
        int maxEvents = Math.max(1, cfg.getReplayMaxEvents());
        boolean overLimit = processed >= maxEvents;

        if (exhausted || overLimit) {
            task.setStatus("DONE");
            task.setNote(overLimit ? "达到单任务事件量上限，截断完成" : null);
            log.info("[event] replay done, replayNo={}, replayed={}, skipped={}, failed={}",
                    task.getReplayNo(), replayed, skipped, failed);
        } else {
            task.setStatus("RUNNING");
        }
        replayMapper.updateById(task);
    }

    /** 将事件存储行重建为 Outbox 语义并以 replay=true 分发；返回分发结果（失败不抛出）。 */
    private EventDispatcher.DispatchResult dispatchReplay(EventRecord rec, EventReplayTask task) {
        try {
            EventOutbox row = new EventOutbox();
            row.setId(rec.getId());
            row.setEventId(rec.getEventId());
            row.setEventType(rec.getEventType());
            row.setVersion(rec.getVersion());
            row.setAggregateId(rec.getAggregateId());
            row.setTraceId(rec.getTraceId());
            row.setPayload(rec.getPayload());
            row.setStatus(EventOutboxStatus.PENDING.name());
            row.setRetryCount(0);
            row.setNextRetryAt(LocalDateTime.now());
            row.setCreatedAt(rec.getOccurredAt());
            // 不真实回写 Outbox 表：仅借其结构承载信封；状态由内存行持有
            return dispatcher.dispatch(row, true);
        } catch (RuntimeException e) {
            log.warn("[event] replay dispatch failed, eventId={}", rec.getEventId(), e);
            return new EventDispatcher.DispatchResult(false, 0, 0);
        }
    }

    /**
     * 死信重投（REQ-08）：将 DEAD 事件重置为 PENDING 交由补偿调度重试。
     *
     * @param eventId 指定事件 ID；为空则重投全部死信
     * @return 重投条数（0 条抛 EVENT_DEAD_LETTER_EMPTY）
     */
    public int redriveDeadLetters(long adminId, String eventId) {
        LambdaQueryWrapper<EventOutbox> w = new LambdaQueryWrapper<EventOutbox>()
                .eq(EventOutbox::getStatus, EventOutboxStatus.DEAD.name());
        if (eventId != null && !eventId.isBlank()) {
            w.eq(EventOutbox::getEventId, eventId);
        }
        List<EventOutbox> dead = outboxMapper.selectList(w);
        if (dead.isEmpty()) {
            throw new BizException(Code.EVENT_DEAD_LETTER_EMPTY);
        }
        int n = 0;
        for (EventOutbox row : dead) {
            n += outboxMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<EventOutbox>()
                    .eq(EventOutbox::getId, row.getId())
                    .eq(EventOutbox::getStatus, EventOutboxStatus.DEAD.name())
                    .set(EventOutbox::getStatus, EventOutboxStatus.PENDING.name())
                    .set(EventOutbox::getRetryCount, 0)
                    .set(EventOutbox::getNextRetryAt, LocalDateTime.now())
                    .set(EventOutbox::getUpdatedAt, LocalDateTime.now()));
        }
        writeAudit(adminId, ACTION_REDRIVE, "count=" + n + (eventId == null ? "" : ", eventId=" + eventId));
        log.info("[event] dead letter redriven, count={}, operator={}", n, adminId);
        return n;
    }

    /** Outbox 概览：各状态计数 + 死信存量。 */
    public Map<String, Object> outboxStats() {
        Map<String, Object> out = new HashMap<>();
        Map<String, Long> byStatus = new HashMap<>();
        for (Map<String, Object> row : outboxMapper.countByStatus()) {
            Object status = row.get("status");
            Object cnt = row.get("cnt");
            if (status != null && cnt != null) {
                byStatus.put(String.valueOf(status), ((Number) cnt).longValue());
            }
        }
        out.put("byStatus", byStatus);
        out.put("dead", outboxMapper.countDead());
        out.put("scanBatchSize", props.getEvent().getScanBatchSize());
        return out;
    }

    /** 事件存储分页查询（类型 / 聚合根 / 时间窗过滤）。 */
    public Page<EventRecord> listEvents(String eventType, String aggregateId,
                                        LocalDateTime fromTime, LocalDateTime toTime,
                                        int page, int size) {
        LambdaQueryWrapper<EventRecord> w = new LambdaQueryWrapper<>();
        if (eventType != null && !eventType.isBlank()) {
            w.eq(EventRecord::getEventType, eventType);
        }
        if (aggregateId != null && !aggregateId.isBlank()) {
            w.eq(EventRecord::getAggregateId, aggregateId);
        }
        if (fromTime != null) {
            w.ge(EventRecord::getOccurredAt, fromTime);
        }
        if (toTime != null) {
            w.le(EventRecord::getOccurredAt, toTime);
        }
        w.orderByDesc(EventRecord::getId);
        return recordMapper.selectPage(new Page<>(page, Math.min(Math.max(size, 1), 200)), w);
    }

    /** 回放任务列表（最新在前）。 */
    public Page<EventReplayTask> listReplays(int page, int size) {
        return replayMapper.selectPage(new Page<>(page, Math.min(Math.max(size, 1), 200)),
                new LambdaQueryWrapper<EventReplayTask>().orderByDesc(EventReplayTask::getId));
    }

    // ===== 审计辅助 =====

    private void writeAudit(long adminId, String action, String detail) {
        try {
            AuditLog log = new AuditLog();
            log.setOperatorId(adminId);
            log.setAction(action);
            log.setTargetType("event");
            log.setTargetId(String.valueOf(adminId));
            log.setDetail(detail);
            auditLogMapper.insert(log);
        } catch (Exception ignore) {
            // 审计写失败不应阻断主流程
        }
    }
}
