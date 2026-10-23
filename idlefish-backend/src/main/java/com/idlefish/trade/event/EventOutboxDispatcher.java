package com.idlefish.trade.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.observability.TraceContext;
import com.idlefish.trade.event.mapper.EventOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 定时补偿分发器（ITER-T06 / REQ-06）。
 * <p>
 * afterCommit 即时分发失败（进程崩溃 / 监听器异常 / DB 抖动）的事件行
 * 会停留在 PENDING / FAILED 且 {@code next_retry_at} 到期；
 * 本调度器按批次扫描并重新分发，指数退避，重试耗尽转 DEAD（REQ-08 死信）。
 * <p>死信不入自动重投：经治理端点 {@code POST /api/admin/event/dead-letter/redrive}
 * 人工重置为 PENDING 后由本调度器自然捞起（REQ-08 可重投）。
 * DEAD 积压超过阈值时打点 {@code event.dead.letter.stock} 供告警。
 * <p>每次扫描前后由 {@link TraceContext#ensure()} 注入 traceId，
 * 补偿日志可独立追踪。
 */
@Slf4j
@Component
public class EventOutboxDispatcher {

    private final EventOutboxMapper outboxMapper;
    private final EventDispatcher dispatcher;
    private final MetricsRegistry metrics;
    private final IdlefishProperties props;

    public EventOutboxDispatcher(EventOutboxMapper outboxMapper,
                                 EventDispatcher dispatcher,
                                 MetricsRegistry metrics,
                                 IdlefishProperties props) {
        this.outboxMapper = outboxMapper;
        this.dispatcher = dispatcher;
        this.metrics = metrics;
        this.props = props;
    }

    /**
     * 每 30 秒扫描一批到期待分发事件。
     * 固定延迟（fixedDelay）而非固定速率：积压严重时自动降低扫描频率，保护主库。
     */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 15_000L)
    public void scanAndDispatch() {
        TraceContext.ensure();
        try {
            int batchSize = Math.max(1, props.getEvent().getScanBatchSize());
            List<EventOutbox> batch = outboxMapper.selectDueBatch(LocalDateTime.now(), batchSize);
            if (batch.isEmpty()) {
                reportDeadStock();
                return;
            }
            int success = 0;
            int failure = 0;
            for (EventOutbox row : batch) {
                try {
                    // 先 CAS 抢占为 PENDING + nextRetryAt 推后，防多实例重复分发（单体下防御性保留）
                    if (!claim(row)) {
                        continue;
                    }
                    if (dispatcher.dispatch(row, false).isOk()) {
                        success++;
                    } else {
                        failure++;
                    }
                } catch (RuntimeException e) {
                    failure++;
                    log.warn("[event] scan dispatch failed, eventId={}", row.getEventId(), e);
                }
            }
            metrics.increment("event.scan.round");
            if (success + failure > 0) {
                log.info("[event] scan dispatched, batch={}, success={}, failure={}",
                        batch.size(), success, failure);
            }
            reportDeadStock();
        } catch (RuntimeException e) {
            metrics.increment("event.scan.failure");
            log.error("[event] scan round failed", e);
        }
    }

    /** 死信积压打点（每轮扫描附带，供阈值告警）。 */
    private void reportDeadStock() {
        long dead = outboxMapper.countDead();
        if (dead > 0) {
            metrics.increment("event.dead.letter.stock");
            log.warn("[event] dead letter stock={}, requires manual redrive", dead);
        }
    }

    /** 防并发抢占：仅当行仍为 PENDING/FAILED 且未被人改走时置回 PENDING 并推后 nextRetryAt。 */
    private boolean claim(EventOutbox row) {
        int updated = outboxMapper.update(null, new LambdaUpdateWrapper<EventOutbox>()
                .eq(EventOutbox::getId, row.getId())
                .in(EventOutbox::getStatus, EventOutboxStatus.PENDING.name(), EventOutboxStatus.FAILED.name())
                .eq(EventOutbox::getNextRetryAt, row.getNextRetryAt())
                .set(EventOutbox::getNextRetryAt, LocalDateTime.now().plusSeconds(60))
                .set(EventOutbox::getUpdatedAt, LocalDateTime.now()));
        if (updated > 0) {
            row.setStatus(EventOutboxStatus.PENDING.name());
            row.setNextRetryAt(LocalDateTime.now().plusSeconds(60));
            return true;
        }
        return false;
    }
}
