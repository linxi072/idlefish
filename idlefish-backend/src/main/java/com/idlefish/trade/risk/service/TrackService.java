package com.idlefish.trade.risk.service;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.web.DeviceContext;
import com.idlefish.trade.common.web.DeviceContexts;
import com.idlefish.trade.risk.entity.TrackEvent;
import com.idlefish.trade.risk.mapper.TrackEventMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 埋点服务：记录用户行为事件、采集设备指纹，并交由风控引擎判定。
 */
@Service
public class TrackService {

    private final TrackEventMapper trackEventMapper;
    private final RiskEngine riskEngine;
    private final DeviceFingerprintService deviceFingerprintService;
    private final IdlefishProperties props;

    public TrackService(TrackEventMapper trackEventMapper, RiskEngine riskEngine,
                       DeviceFingerprintService deviceFingerprintService, IdlefishProperties props) {
        this.trackEventMapper = trackEventMapper;
        this.riskEngine = riskEngine;
        this.deviceFingerprintService = deviceFingerprintService;
        this.props = props;
    }

    /** 标准埋点（从请求上下文自动采集设备信号）。 */
    public void track(Long userId, String event, String bizId, String ext) {
        track(userId, event, bizId, ext, DeviceContexts.current());
    }

    /** 显式指定设备上下文的埋点（测试/异步场景用）。 */
    public void track(Long userId, String event, String bizId, String ext, DeviceContext dc) {
        if (!isValidEvent(event)) {
            throw new com.idlefish.trade.common.BizException(com.idlefish.trade.common.Code.PARAM_INVALID, "未知埋点事件: " + event);
        }
        // 设备指纹采集（仅当开启且具备设备信号）
        if (props.getRisk().isDeviceEnabled() && dc != null && dc.hasDeviceId()) {
            try {
                deviceFingerprintService.record(userId, dc);
            } catch (Exception ignore) {
                // 指纹采集失败不影响主流程
            }
        }
        TrackEvent te = new TrackEvent();
        te.setUserId(userId);
        te.setEvent(event);
        te.setBizId(bizId);
        te.setExt(ext);
        if (dc != null) {
            te.setDeviceId(dc.getDeviceId());
            te.setIp(dc.getIp());
            te.setUa(dc.getUa());
        }
        trackEventMapper.insert(te);
        riskEngine.evaluate(te);
    }

    public List<String> eventCatalog() {
        return Arrays.asList(RiskEngine.EVENTS);
    }

    private boolean isValidEvent(String event) {
        if (event == null) {
            return false;
        }
        for (String e : RiskEngine.EVENTS) {
            if (e.equals(event)) {
                return true;
            }
        }
        return false;
    }
}
