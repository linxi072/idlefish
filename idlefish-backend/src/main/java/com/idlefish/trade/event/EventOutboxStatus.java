package com.idlefish.trade.event;

/**
 * Outbox 分发状态机（ITER-T06 / REQ-05/06/08）。
 * <pre>
 * PENDING --分发成功--> SENT
 *    |  \--分发失败(重试未耗尽)--> FAILED --到期重扫--> PENDING(重试)
 *    \---分发失败(重试耗尽)------> DEAD --人工/接口重投--> PENDING
 * </pre>
 */
public enum EventOutboxStatus {

    /** 待分发（新写入 / 重试到期 / 死信重投后）。 */
    PENDING,
    /** 已成功分发至全部匹配监听器（终态）。 */
    SENT,
    /** 分发失败等待重试（未达最大重试次数）。 */
    FAILED,
    /** 死信（重试耗尽，等待人工介入或重投，REQ-08）。 */
    DEAD
}
