package com.idlefish.trade.event;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领域事件信封（ITER-T06 事件驱动架构 / REQ-01 七要素）。
 * <ul>
 *   <li>{@code eventId}：全局唯一事件 ID（32 位 UUID hex），消费端幂等键的组成部分；</li>
 *   <li>{@code eventType}：事件类型 code（见 {@link EventType}），如 {@code ORDER_PAID}；</li>
 *   <li>{@code version}：schema 版本号（REQ-02 版本化），消费端按版本兼容解析；</li>
 *   <li>{@code occurredAt}：业务发生时间（发布时刻）；</li>
 *   <li>{@code aggregateId}：聚合根标识（订单号 / 支付单号 / 商品 ID 等）；</li>
 *   <li>{@code traceId}：发布时的链路 ID，消费端经 {@code EventConsumeTemplate} 注入 MDC（REQ-10）；</li>
 *   <li>{@code payload}：业务负载（反序列化后的 Map/List，见 {@link #payloadJson} 原始串）。</li>
 * </ul>
 * <p>金额类负载字段单位一律「分」（项目全局约定）。</p>
 * <p>{@code replay} 标志（REQ-13）：由回放链路置为 {@code true}，
 * 副作用消费者（{@link DomainEventListener#hasSideEffect()}）应跳过执行。</p>
 */
@Data
public class DomainEvent {

    /** 全局唯一事件 ID（32 位 UUID hex）。 */
    private String eventId;

    /** 事件类型 code（见 {@link EventType}）。 */
    private String eventType;

    /** schema 版本号（REQ-02），随 {@link EventType#getVersion()} 写入。 */
    private Integer version;

    /** 业务发生时间（发布时刻）。 */
    private LocalDateTime occurredAt;

    /** 聚合根标识（订单号 / 支付单号 / 商品 ID 等）。 */
    private String aggregateId;

    /** 发布链路 traceId（REQ-10 跨异步透传）。 */
    private String traceId;

    /** 业务负载（JSON 反序列化后的对象，通常为 Map）。 */
    private Object payload;

    /** 业务负载原始 JSON 串（供消费者按需二次解析）。 */
    private String payloadJson;

    /** 是否回放流量（REQ-13）：true 时副作用消费者必须跳过。 */
    private boolean replay;
}
