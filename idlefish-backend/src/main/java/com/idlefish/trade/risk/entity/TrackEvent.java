package com.idlefish.trade.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;

import java.io.Serializable;

/**
 * 埋点事件（PRD §6 数据埋点）：记录 12 类用户行为，供分析与风控使用。
 */
@TableName("t_track_event")
public class TrackEvent extends BaseEntity implements Serializable {

    private Long userId;
    /** 事件类型：register/login/publish/view_item/search/favorite/order_create/pay/ship/confirm_receive/refund_apply/comment/share */
    private String event;
    private String bizId;
    private String ext;
    private String deviceId;
    private String ip;
    private String ua;

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

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUa() {
        return ua;
    }

    public void setUa(String ua) {
        this.ua = ua;
    }
}
