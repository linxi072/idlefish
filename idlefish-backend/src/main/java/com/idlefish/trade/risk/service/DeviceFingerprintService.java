package com.idlefish.trade.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.SignUtils;
import com.idlefish.trade.common.web.DeviceContext;
import com.idlefish.trade.risk.entity.DeviceFingerprint;
import com.idlefish.trade.risk.mapper.DeviceFingerprintMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 设备指纹服务：采集并维护「设备-账号」绑定关系，提供新设备识别、同设备多账号计数、
 * 黑名单命中判定，支撑风控规则库 R3/R4/R6。
 */
@Service
public class DeviceFingerprintService {

    private final DeviceFingerprintMapper mapper;
    private final IdlefishProperties props;

    public DeviceFingerprintService(DeviceFingerprintMapper mapper, IdlefishProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    /** 记录一次设备-账号绑定（幂等 upsert），返回绑定记录。 */
    public DeviceFingerprint record(Long userId, DeviceContext dc) {
        if (dc == null || !dc.hasDeviceId()) {
            return null;
        }
        String deviceId = dc.getDeviceId();
        String ua = dc.getUa() == null ? "" : dc.getUa();
        String fpHash = SignUtils.sha256Hex(deviceId + "|" + ua);
        LocalDateTime now = LocalDateTime.now();

        DeviceFingerprint existing = mapper.selectByDeviceUser(deviceId, userId);
        if (existing == null) {
            DeviceFingerprint f = new DeviceFingerprint();
            f.setDeviceId(deviceId);
            f.setUserId(userId);
            f.setFpHash(fpHash);
            f.setFirstSeen(now);
            f.setLastSeen(now);
            f.setStatus("normal");
            mapper.insert(f);
            return f;
        }
        existing.setLastSeen(now);
        existing.setFpHash(fpHash);
        mapper.updateById(existing);
        return existing;
    }

    /** 同一设备绑定的账号数（用于 R4 群控/接码识别）。 */
    public int countUsers(String deviceId) {
        if (deviceId == null) {
            return 0;
        }
        return mapper.countUsersByDevice(deviceId);
    }

    /** 是否新设备：首次出现于 24 小时内（用于 R3 新设备大额下单）。 */
    public boolean isNewDevice(String deviceId) {
        if (deviceId == null) {
            return false;
        }
        LocalDateTime earliest = mapper.earliestSeen(deviceId);
        if (earliest == null) {
            return true;
        }
        return earliest.isAfter(LocalDateTime.now().minusHours(24));
    }

    /** 是否命中设备黑名单（配置或库内标记，用于 R6）。 */
    public boolean isBlacklisted(String deviceId) {
        if (deviceId == null) {
            return false;
        }
        if (props.getRisk().getBlacklistDeviceIds().contains(deviceId)) {
            return true;
        }
        DeviceFingerprint f = mapper.selectOne(new LambdaQueryWrapper<DeviceFingerprint>()
                .eq(DeviceFingerprint::getDeviceId, deviceId)
                .eq(DeviceFingerprint::getStatus, "blacklisted")
                .last("LIMIT 1"));
        return f != null;
    }
}
