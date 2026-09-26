package com.idlefish.trade.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.risk.entity.RiskEvent;
import com.idlefish.trade.risk.entity.TrackEvent;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import org.springframework.stereotype.Service;

/**
 * 风控引擎（F-15.1 工程化）：
 * 规则判定统一委托 {@link RiskRuleEngine}（可配置规则库 + 纯函数 {@link RiskRuleFunctions}），
 * 本类负责将命中结果落地为 {@link RiskEvent} 供核查/结算冻结，并叠加异常评分卡 {@link RiskScorer} 强化高危识别。
 *
 * <p>冻结联动：落地 status=open 的 RiskEvent 后，{@code SettlementService.processDue}
 * 会在结算时查询卖家未处置事件并置 frozen，复用既有链路，无需改动结算侧。</p>
 */
@Service
public class RiskEngine {

    /** 12 类埋点事件清单（用于校验与文档）。 */
    public static final String[] EVENTS = {
            "register", "login", "publish", "view_item", "search",
            "favorite", "order_create", "pay", "ship", "confirm_receive",
            "refund_apply", "comment", "share"
    };

    private final RiskRuleEngine ruleEngine;
    private final RiskEventMapper riskEventMapper;
    private final DeviceFingerprintService deviceFingerprintService;
    private final IdlefishProperties props;
    private final MetricsRegistry metrics;

    public RiskEngine(RiskRuleEngine ruleEngine, RiskEventMapper riskEventMapper,
                      DeviceFingerprintService deviceFingerprintService, IdlefishProperties props,
                      MetricsRegistry metrics) {
        this.ruleEngine = ruleEngine;
        this.riskEventMapper = riskEventMapper;
        this.deviceFingerprintService = deviceFingerprintService;
        this.props = props;
        this.metrics = metrics;
    }

    /** 事件接入后执行风控判定（委托可配置规则库 + 评分卡）。 */
    public void evaluate(TrackEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }
        // 1) 可配置规则库判定 → 命中落地
        for (RuleHit hit : ruleEngine.evaluate(event)) {
            raise(event, hit.getCode(), hit.getName(), hit.getLevel(), hit.getBizType(), hit.getBizId());
            metrics.increment("risk.rule_hit");
        }
        // 2) 异常评分卡：在「落库前」统计既有未处置事件，避免本次评估自膨胀
        int openRisk = countOpen(event.getUserId());
        RiskScorer.RiskBand band = RiskScorer.score(buildSignals(event, openRisk));
        if ("high".equals(band.getBand())) {
            String bizType = event.getDeviceId() != null ? "device" : "user";
            String bizId = event.getDeviceId() != null ? event.getDeviceId() : event.getBizId();
            raise(event, "R_SCORE_HIGH", "异常评分高危", "high", bizType, bizId);
            metrics.increment("risk.score_high");
        }
    }

    private RiskScorer.RiskSignals buildSignals(TrackEvent event, int openRisk) {
        IdlefishProperties.Risk r = props.getRisk();
        boolean devEnabled = r.isDeviceEnabled();
        String deviceId = event.getDeviceId();
        boolean newDev = false, blacklisted = false;
        int bindUsers = 0;
        if (deviceId != null && devEnabled) {
            newDev = deviceFingerprintService.isNewDevice(deviceId);
            blacklisted = deviceFingerprintService.isBlacklisted(deviceId);
            bindUsers = deviceFingerprintService.countUsers(deviceId);
        }
        long amount = parseAmount(event.getExt());
        return new RiskScorer.RiskSignals(openRisk, newDev, bindUsers, blacklisted,
                amount, r.getHighAmountThresholdFen(), r.getDeviceAccountThreshold());
    }

    private int countOpen(Long userId) {
        Long c = riskEventMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                .eq(RiskEvent::getUserId, userId)
                .eq(RiskEvent::getStatus, "open"));
        return c == null ? 0 : c.intValue();
    }

    private long parseAmount(String ext) {
        if (ext == null || ext.isBlank()) {
            return 0L;
        }
        try {
            if (ext.trim().startsWith("{")) {
                com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(ext);
                return n.path("amount").asLong(0L);
            }
            return Long.parseLong(ext.trim());
        } catch (Exception e) {
            return 0L;
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
