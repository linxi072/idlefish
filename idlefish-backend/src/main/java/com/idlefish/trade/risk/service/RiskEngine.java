package com.idlefish.trade.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.risk.entity.RiskEvent;
import com.idlefish.trade.risk.entity.TrackEvent;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.risk.mapper.TrackEventMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 风控引擎骨架（PRD §5 风控）：
 * 基于埋点事件做轻量规则判定，命中后落地 RiskEvent 供核查。
 * 生产可替换为规则引擎（如 Drools）或模型服务。
 */
@Service
public class RiskEngine {

    /** 12 类埋点事件清单（用于校验与文档）。 */
    public static final String[] EVENTS = {
            "register", "login", "publish", "view_item", "search",
            "favorite", "order_create", "pay", "ship", "confirm_receive",
            "refund_apply", "comment", "share"
    };

    private final TrackEventMapper trackEventMapper;
    private final RiskEventMapper riskEventMapper;

    public RiskEngine(TrackEventMapper trackEventMapper, RiskEventMapper riskEventMapper) {
        this.trackEventMapper = trackEventMapper;
        this.riskEventMapper = riskEventMapper;
    }

    /** 事件接入后执行风控判定（骨架：示例规则）。 */
    public void evaluate(TrackEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }
        // 规则 R1：短时间内高频发布（1 分钟内 >= 5 次 publish）→ 疑似垃圾发布
        if ("publish".equals(event.getEvent())) {
            long cnt = trackEventMapper.selectCount(new LambdaQueryWrapper<TrackEvent>()
                    .eq(TrackEvent::getUserId, event.getUserId())
                    .eq(TrackEvent::getEvent, "publish")
                    .ge(TrackEvent::getCreatedAt, LocalDateTime.now().minusMinutes(1)));
            if (cnt >= 5) {
                raise(event, "R1_HIGH_PUBLISH", "高频发布", "mid", "publish", event.getBizId());
            }
        }
        // 规则 R2：高频退款申请（30 分钟内 >= 3 次 refund_apply）→ 疑似退款滥用
        if ("refund_apply".equals(event.getEvent())) {
            long cnt = trackEventMapper.selectCount(new LambdaQueryWrapper<TrackEvent>()
                    .eq(TrackEvent::getUserId, event.getUserId())
                    .eq(TrackEvent::getEvent, "refund_apply")
                    .ge(TrackEvent::getCreatedAt, LocalDateTime.now().minusMinutes(30)));
            if (cnt >= 3) {
                raise(event, "R2_REFUND_ABUSE", "退款滥用", "high", "refund", event.getBizId());
            }
        }
    }

    private void raise(TrackEvent event, String code, String name, String level,
                       String bizType, String bizId) {
        RiskEvent re = new RiskEvent();
        re.setUserId(event.getUserId());
        re.setRuleCode(code);
        re.setRuleName(name);
        re.setLevel(level);
        re.setBizType(bizType);
        re.setBizId(bizId);
        re.setStatus("open");
        riskEventMapper.insert(re);
    }
}
