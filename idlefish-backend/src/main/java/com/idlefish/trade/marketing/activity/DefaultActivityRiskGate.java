package com.idlefish.trade.marketing.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.risk.entity.RiskEvent;
import com.idlefish.trade.risk.entity.TrackEvent;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.risk.service.RiskEngine;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 默认风控钩子（ITER-F08 / REQ-05）：委托既有 {@link RiskEngine} 评估并落地风险事件，
 * 再以「该活动参与是否触发高危开放事件」作为拒绝判据。
 * <p>
 * 评估为 best-effort：风控引擎异常时放行（不阻断活动主链路），由结算侧冻结联动兜底。
 */
@Component
public class DefaultActivityRiskGate implements ActivityRiskGate {

    private static final String BIZ_TYPE = "activity_join";

    private final RiskEngine riskEngine;
    private final RiskEventMapper riskEventMapper;

    public DefaultActivityRiskGate(RiskEngine riskEngine, RiskEventMapper riskEventMapper) {
        this.riskEngine = riskEngine;
        this.riskEventMapper = riskEventMapper;
    }

    @Override
    public boolean allow(Long userId, String deviceId, Long activityId, String kind) {
        if (userId == null) {
            return false;
        }
        TrackEvent event = new TrackEvent();
        event.setUserId(userId);
        event.setDeviceId(deviceId);
        event.setEvent(BIZ_TYPE);
        event.setBizId(String.valueOf(activityId));
        event.setExt("{\"kind\":\"" + (kind == null ? "" : kind) + "\"}");
        try {
            riskEngine.evaluate(event);
        } catch (RuntimeException ignored) {
            // 风控异常不阻断主链路（best-effort）
            return true;
        }
        // 该活动参与触发的高危开放事件 → 拒绝
        Long hits = riskEventMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                .eq(RiskEvent::getUserId, userId)
                .eq(RiskEvent::getBizType, BIZ_TYPE)
                .eq(RiskEvent::getBizId, String.valueOf(activityId))
                .eq(RiskEvent::getLevel, "high")
                .eq(RiskEvent::getStatus, "open")
                .ge(RiskEvent::getCreatedAt, LocalDateTime.now().minusSeconds(5)));
        return hits == null || hits == 0;
    }
}
