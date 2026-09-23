package com.idlefish.trade.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备指纹：同一设备可绑定多个账号，用于群控/接码识别、设备维度风控与黑名单。
 */
@TableName("t_device_fingerprint")
public class DeviceFingerprint extends BaseEntity implements Serializable {

    private String deviceId;
    private Long userId;
    private String fpHash;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private String status = "normal"; // normal / blacklisted

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFpHash() {
        return fpHash;
    }

    public void setFpHash(String fpHash) {
        this.fpHash = fpHash;
    }

    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(LocalDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
