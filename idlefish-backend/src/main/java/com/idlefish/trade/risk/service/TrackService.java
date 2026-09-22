package com.idlefish.trade.risk.service;

import com.idlefish.trade.risk.entity.TrackEvent;
import com.idlefish.trade.risk.mapper.TrackEventMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 埋点服务：记录用户行为事件并交由风控引擎判定。
 */
@Service
public class TrackService {

    private final TrackEventMapper trackEventMapper;
    private final RiskEngine riskEngine;

    public TrackService(TrackEventMapper trackEventMapper, RiskEngine riskEngine) {
        this.trackEventMapper = trackEventMapper;
        this.riskEngine = riskEngine;
    }

    public void track(Long userId, String event, String bizId, String ext) {
        if (!isValidEvent(event)) {
            throw new com.idlefish.trade.common.BizException(com.idlefish.trade.common.Code.PARAM_INVALID, "未知埋点事件: " + event);
        }
        TrackEvent te = new TrackEvent();
        te.setUserId(userId);
        te.setEvent(event);
        te.setBizId(bizId);
        te.setExt(ext);
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
