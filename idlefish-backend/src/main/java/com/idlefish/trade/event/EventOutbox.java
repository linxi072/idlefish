package com.idlefish.trade.event;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 事件发件箱（Outbox，ITER-T06 / REQ-05 不丢事件）。
 * <p>
 * 与业务事务<b>同库同事务</b>写入：业务提交则事件必然落库；
 * 提交后由 afterCommit 本地分发（失败由 {@code EventOutboxDispatcher} 定时补偿重试）。
 * 本表是「事件是否需要分发」的事实源，删除/清理仅影响重试能力，不影响已消费语义。
 * <ul>
 *   <li>{@code status}：见 {@link EventOutboxStatus}；</li>
 *   <li>{@code retryCount}：失败重试次数（重试耗尽转 DEAD）；</li>
 *   <li>{@code nextRetryAt}：下次重试时间（指数退避）；</li>
 *   <li>{@code lastError}：最近一次分发失败原因（截断存储，便于排查）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_event_outbox")
public class EventOutbox extends BaseEntity {

    /** 全局唯一事件 ID（与 {@link DomainEvent#getEventId()} 一致）。 */
    private String eventId;

    /** 事件类型 code（见 {@link EventType}）。 */
    private String eventType;

    /** schema 版本号（REQ-02）。 */
    private Integer version;

    /** 聚合根标识（订单号 / 支付单号等）。 */
    private String aggregateId;

    /** 发布链路 traceId（REQ-10）。 */
    private String traceId;

    /** 业务负载 JSON（金额字段单位「分」）。 */
    private String payload;

    /** 分发状态（见 {@link EventOutboxStatus}）。 */
    private String status;

    /** 失败重试次数。 */
    private Integer retryCount;

    /** 下次重试时间（指数退避；PENDING 态始终可被扫描）。 */
    private LocalDateTime nextRetryAt;

    /** 最近一次分发失败原因（截断至 500 字符）。 */
    private String lastError;
}
