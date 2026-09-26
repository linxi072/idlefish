package com.idlefish.trade.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.risk.entity.RiskRule;
import com.idlefish.trade.risk.entity.TrackEvent;
import com.idlefish.trade.risk.mapper.RiskRuleMapper;
import com.idlefish.trade.risk.mapper.TrackEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 可配置风控规则引擎（F-15.1）：
 * 规则定义持久化于 {@code t_risk_rule}，启动时加载到 AtomicReference 活跃集，支持热加载。
 * evaluate 完成计数 / 设备指纹等 IO 后，交由纯函数 {@link RiskRuleFunctions} 判定，返回命中清单（不落库）。
 */
@Service
public class RiskRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RiskRuleEngine.class);

    private final RiskRuleMapper ruleMapper;
    private final TrackEventMapper trackEventMapper;
    private final DeviceFingerprintService deviceFingerprintService;
    private final IdlefishProperties props;
    private final AtomicReference<List<RiskRule>> activeRules = new AtomicReference<>(List.of());

    public RiskRuleEngine(RiskRuleMapper ruleMapper, TrackEventMapper trackEventMapper,
                          DeviceFingerprintService deviceFingerprintService, IdlefishProperties props) {
        this.ruleMapper = ruleMapper;
        this.trackEventMapper = trackEventMapper;
        this.deviceFingerprintService = deviceFingerprintService;
        this.props = props;
    }

    @PostConstruct
    public void load() {
        try {
            List<RiskRule> rules = orderedRules();
            if (rules.isEmpty()) {
                seedDefaults();
                rules = orderedRules();
            }
            activeRules.set(rules);
        } catch (Exception e) {
            // 启动期 DB 不可用时降级为空规则集，避免应用无法启动；后续 reload 可恢复
            log.warn("风控规则加载失败，降级为空规则集: {}", e.getMessage());
        }
    }

    /** 当前活跃规则（运营后台展示用）。 */
    public List<RiskRule> listActive() {
        return new ArrayList<>(activeRules.get());
    }

    /** 重新从 DB 拉取并刷新活跃规则集（热加载）。 */
    public void reload() {
        try {
            List<RiskRule> rules = orderedRules();
            if (rules.isEmpty()) {
                seedDefaults();
                rules = orderedRules();
            }
            activeRules.set(rules);
        } catch (Exception e) {
            log.warn("风控规则热加载失败，维持现有规则集: {}", e.getMessage());
        }
    }

    /** 批量保存（新增 / 更新）并热加载。 */
    public void saveRules(List<RiskRule> rules) {
        if (rules == null) {
            return;
        }
        for (RiskRule r : rules) {
            if (r.getId() == null) {
                ruleMapper.insert(r);
            } else {
                ruleMapper.updateById(r);
            }
        }
        reload();
    }

    /** 切换单条规则启用状态并热加载。 */
    public void toggle(String code, int enabled) {
        RiskRule r = ruleMapper.selectOne(new LambdaQueryWrapper<RiskRule>().eq(RiskRule::getCode, code));
        if (r == null) {
            throw new BizException(Code.NOT_FOUND, "风控规则不存在: " + code);
        }
        r.setEnabled(enabled);
        ruleMapper.updateById(r);
        reload();
    }

    /** 对一条埋点事件执行全部活跃规则，返回命中清单（不落库）。 */
    public List<RuleHit> evaluate(TrackEvent event) {
        List<RuleHit> hits = new ArrayList<>();
        for (RiskRule rule : activeRules.get()) {
            if (rule.getEnabled() != 1) {
                continue;
            }
            RuleInput input = buildInput(rule, event);
            if (input == null) {
                continue;
            }
            String level = RiskRuleFunctions.eval(rule, input);
            if (level != null) {
                hits.add(new RuleHit(rule.getCode(), rule.getName(), level, bizTypeOf(rule), bizIdOf(rule, event)));
            }
        }
        return hits;
    }

    private RuleInput buildInput(RiskRule rule, TrackEvent event) {
        boolean deviceEnabled = props.getRisk().isDeviceEnabled();
        switch (rule.getType()) {
            case RiskRuleType.FREQ_PUBLISH:
                if (!"publish".equals(event.getEvent())) {
                    return null;
                }
                long pubCnt = countWindow(event.getUserId(), "publish", rule.getWindowMin());
                return new RuleInput("publish", 0, pubCnt, false, 0, false);
            case RiskRuleType.REFUND_ABUSE:
                if (!"refund_apply".equals(event.getEvent())) {
                    return null;
                }
                long refundCnt = countWindow(event.getUserId(), "refund_apply", rule.getWindowMin());
                return new RuleInput("refund_apply", 0, refundCnt, false, 0, false);
            case RiskRuleType.NEW_DEVICE_PAY:
                if (!("order_create".equals(event.getEvent()) || "pay".equals(event.getEvent()))) {
                    return null;
                }
                String deviceId = event.getDeviceId();
                if (deviceId == null || !deviceEnabled) {
                    return null;
                }
                long amount = parseAmount(event.getExt());
                boolean newDev = deviceFingerprintService.isNewDevice(deviceId);
                return new RuleInput(event.getEvent(), amount, 0, newDev, 0, false);
            case RiskRuleType.DEVICE_MULTI_ACCOUNT:
                deviceId = event.getDeviceId();
                if (deviceId == null || !deviceEnabled) {
                    return null;
                }
                int bind = deviceFingerprintService.countUsers(deviceId);
                return new RuleInput(event.getEvent(), 0, 0, false, bind, false);
            case RiskRuleType.DEVICE_ORDER_RATE:
                deviceId = event.getDeviceId();
                if (deviceId == null || !deviceEnabled) {
                    return null;
                }
                long orderCnt = countWindowDevice(deviceId, "order_create", rule.getWindowMin());
                return new RuleInput("order_create", 0, orderCnt, false, 0, false);
            case RiskRuleType.DEVICE_BLACKLIST:
                deviceId = event.getDeviceId();
                if (deviceId == null || !deviceEnabled) {
                    return null;
                }
                boolean bl = deviceFingerprintService.isBlacklisted(deviceId);
                return new RuleInput(event.getEvent(), 0, 0, false, 0, bl);
            default:
                return null;
        }
    }

    private long countWindow(Long userId, String event, int windowMin) {
        return trackEventMapper.selectCount(new LambdaQueryWrapper<TrackEvent>()
                .eq(TrackEvent::getUserId, userId)
                .eq(TrackEvent::getEvent, event)
                .ge(TrackEvent::getCreatedAt, LocalDateTime.now().minusMinutes(Math.max(1, windowMin))));
    }

    private long countWindowDevice(String deviceId, String event, int windowMin) {
        return trackEventMapper.selectCount(new LambdaQueryWrapper<TrackEvent>()
                .eq(TrackEvent::getDeviceId, deviceId)
                .eq(TrackEvent::getEvent, event)
                .ge(TrackEvent::getCreatedAt, LocalDateTime.now().minusMinutes(Math.max(1, windowMin))));
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

    private String bizTypeOf(RiskRule rule) {
        return "device".equals(rule.getScope()) ? "device" : "order";
    }

    private String bizIdOf(RiskRule rule, TrackEvent event) {
        return "device".equals(rule.getScope()) ? event.getDeviceId() : event.getBizId();
    }

    private List<RiskRule> orderedRules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<RiskRule>().orderByAsc(RiskRule::getPriority));
    }

    /** 首次启动种子默认规则（原 R1~R6），阈值取自 IdlefishProperties 默认值。 */
    private void seedDefaults() {
        IdlefishProperties.Risk r = props.getRisk();
        ruleMapper.insert(of("R1_HIGH_PUBLISH", RiskRuleType.FREQ_PUBLISH, "高频发布", "mid", 1, "user",
                5, 1, 10, "1 分钟内同用户发布 >=5 次，疑似垃圾发布"));
        ruleMapper.insert(of("R2_REFUND_ABUSE", RiskRuleType.REFUND_ABUSE, "退款滥用", "high", 1, "user",
                3, 30, 20, "30 分钟内同用户退款申请 >=3 次，疑似退款滥用"));
        ruleMapper.insert(of("R3_NEW_DEVICE_PAY", RiskRuleType.NEW_DEVICE_PAY, "新设备大额交易", "mid", 1, "device",
                r.getHighAmountThresholdFen(), 1, 30, "新设备 + 超阈值金额下单 / 支付"));
        ruleMapper.insert(of("R4_DEVICE_MULTI_ACCOUNT", RiskRuleType.DEVICE_MULTI_ACCOUNT, "同设备多账号", "mid", 1, "device",
                r.getDeviceAccountThreshold(), 1, 40, "同设备绑定账号数超阈值（达两倍视为群控 / 接码）"));
        ruleMapper.insert(of("R5_DEVICE_ORDER_RATE", RiskRuleType.DEVICE_ORDER_RATE, "设备下单频次超限", "mid", 1, "device",
                r.getDeviceOrderRate(), Math.max(1, r.getDeviceOrderWindowMin()), 50, "设备维度单位时间下单次数超阈值"));
        ruleMapper.insert(of("R6_DEVICE_BLACKLIST", RiskRuleType.DEVICE_BLACKLIST, "设备黑名单", "high", 1, "device",
                0, 1, 60, "命中设备黑名单，直接高危冻结"));
    }

    private static RiskRule of(String code, String type, String name, String level, int enabled,
                              String scope, long threshold, int windowMin, int priority, String desc) {
        RiskRule r = new RiskRule();
        r.setCode(code);
        r.setType(type);
        r.setName(name);
        r.setLevel(level);
        r.setEnabled(enabled);
        r.setScope(scope);
        r.setThreshold(threshold);
        r.setWindowMin(windowMin);
        r.setPriority(priority);
        r.setDescription(desc);
        return r;
    }
}
