package com.idlefish.trade.event;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 事件回放任务（ITER-T06 / REQ-13：治理端点发起的限流回放）。
 * <p>
 * 管理员选定事件类型 + 时间窗后创建回放任务：从 {@code t_domain_event} 拉取
 * 命中的事件，逐条重新分发（{@code replay=true}），副作用监听器跳过（REQ-13）。
 * 回放按任务行推进、限速执行，失败不阻断后续事件。
 * <ul>
 *   <li>{@code replayNo}：回放单号（EVR 前缀）；</li>
 *   <li>{@code status}：PENDING / RUNNING / DONE / FAILED；</li>
 *   <li>{@code replayedCount} / {@code skippedCount}：已重放 / 因副作用跳过的事件数。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_event_replay")
public class EventReplayTask extends BaseEntity {

    /** 回放单号（EVR 前缀，来自 IdGenerator.replayNo）。 */
    private String replayNo;

    /** 回放的事件类型 code（见 {@link EventType}）。 */
    private String eventType;

    /** 回放时间窗起（含，按事件发生时间过滤）。 */
    private LocalDateTime fromTime;

    /** 回放时间窗止（含）。 */
    private LocalDateTime toTime;

    /** 发起回放的管理员 ID（t_admin_user.id）。 */
    private Long operatorAdminId;

    /** 任务状态：PENDING 待执行 / RUNNING 执行中 / DONE 完成 / FAILED 失败。 */
    private String status;

    /** 已重放（成功分发）事件数。 */
    private Integer replayedCount;

    /** 因副作用跳过的事件数（REQ-13）。 */
    private Integer skippedCount;

    /** 失败事件数（单条失败不阻断任务）。 */
    private Integer failedCount;

    /** 回放游标（最后已处理的事件存储主键；雪花 ID 随时间递增，游标增量拉取免 offset 深翻页）。 */
    private Long cursorId;

    /** 备注（失败原因 / 人工说明）。 */
    private String note;
}
