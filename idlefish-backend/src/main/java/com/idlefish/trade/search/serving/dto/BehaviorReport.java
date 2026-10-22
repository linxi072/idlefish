package com.idlefish.trade.search.serving.dto;

/**
 * 行为回传传输对象（ITER-T04 / REQ-05）：view_item / favorite / order_create / pay / search。
 */
public class BehaviorReport {

    private Long userId;
    private String event;
    private String bizId;

    public BehaviorReport() {
    }

    public BehaviorReport(Long userId, String event, String bizId) {
        this.userId = userId;
        this.event = event;
        this.bizId = bizId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }
}
