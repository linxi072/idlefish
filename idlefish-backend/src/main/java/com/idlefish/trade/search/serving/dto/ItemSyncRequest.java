package com.idlefish.trade.search.serving.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

/**
 * 商品索引同步事件（ITER-T04 / REQ-02）：商品数据变更时经 RocketMQ 异步同步至搜推侧，
 * 亦作为 {@code /sr/api/sync/item} 消费回调与补偿推送的报文。
 */
public class ItemSyncRequest {

    /** 商品 ID。 */
    @NotNull(message = "商品ID必填")
    private Long itemId;
    /** 事件类型：UPSERT（上架/编辑/审核通过）/ REMOVE（下架/删除）。 */
    @NotBlank(message = "事件类型必填")
    private String event;
    /** 事件发生时间（毫秒时间戳，用于滞后度量）。 */
    @NotNull(message = "事件发生时间必填")
    private Long occurredAt;

    public ItemSyncRequest() {
    }

    public ItemSyncRequest(Long itemId, String event, Long occurredAt) {
        this.itemId = itemId;
        this.event = event;
        this.occurredAt = occurredAt;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public Long getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Long occurredAt) {
        this.occurredAt = occurredAt;
    }
}
