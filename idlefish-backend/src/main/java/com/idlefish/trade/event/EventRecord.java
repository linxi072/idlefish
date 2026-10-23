package com.idlefish.trade.event;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 领域事件存储（ITER-T06 / REQ-09：事件持久化 ≥90 天，支持治理端点查询与回放）。
 * <p>仅 {@link EventType#isPersisted()} 为 true 的事件写入；与业务同事务落库，
 * 是事件审计、排障与回放的数据源。由 {@code EventCleanupJob}（如启用）按保留期清理。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_domain_event")
public class EventRecord extends BaseEntity {

    /** 全局唯一事件 ID。 */
    private String eventId;

    /** 事件类型 code（见 {@link EventType}）。 */
    private String eventType;

    /** schema 版本号（REQ-02）。 */
    private Integer version;

    /** 聚合根标识。 */
    private String aggregateId;

    /** 发布链路 traceId（REQ-10）。 */
    private String traceId;

    /** 业务负载 JSON。 */
    private String payload;

    /** 业务发生时间。 */
    private LocalDateTime occurredAt;
}
