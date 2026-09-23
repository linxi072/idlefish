package com.idlefish.trade.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.risk.entity.RiskEvent;
import com.idlefish.trade.risk.entity.TrackEvent;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.risk.mapper.TrackEventMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 风控引擎（PRD §5 风控）：
 * 基于埋点事件 + 设备指纹做轻量规则判定，命中后落地 RiskEvent 供核查。
 * 生产可替换为规则引擎（如 Drools）或模型服务。
 *
 * 规则清单：
 * - R1 高频发布、R2 退款滥用（基础规则）
 * - R3 新设备 + 大额下单（设备维度）
 * - R4 同设备绑定多账号（群控/接码识别）
 * - R5 设备维度下单频次超限
 * - R6 设备黑名单命中（直接高危冻结）
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
    private final DeviceFingerprintService deviceFingerprintService;
    private final IdlefishProperties props;

    public RiskEngine(TrackEventMapper trackEventMapper, RiskEventMapper riskEventMapper,
                      DeviceFingerprintService deviceFingerprintService, IdlefishProperties props) {
        this.trackEventMapper = trackEventMapper;
        this.riskEventMapper = riskEventMapper;
        this.deviceFingerprintService = deviceFingerprintService;
        this.props = props;
    }

    /** 事件接入后执行风控判定。 */
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

        // ===== 设备维度规则（R3/R4/R5/R6）=====
        String deviceId = event.getDeviceId();
        if (deviceId != null && props.getRisk().isDeviceEnabled()) {
            // 规则 R3：新设备 + 大额下单/支付
            if (("order_create".equals(event.getEvent()) || "pay".equals(event.getEvent()))
                    && deviceFingerprintService.isNewDevice(deviceId)) {
                long amount = parseAmount(event.getExt());
                String level = amount >= props.getRisk().getHighAmountThresholdFen() ? "high" : "mid";
                raise(event, "R3_NEW_DEVICE_PAY", "新设备大额交易", level, "order", event.getBizId());
            }
            // 规则 R4：同设备绑定多账号（群控/接码）
            int bindUsers = deviceFingerprintService.countUsers(deviceId);
            if (bindUsers >= props.getRisk().getDeviceAccountThreshold()) {
                String level = bindUsers >= props.getRisk().getDeviceAccountThreshold() * 2 ? "high" : "mid";
                raise(event, "R4_DEVICE_MULTI_ACCOUNT", "同设备多账号", level, "device", deviceId);
            }
            // 规则 R5：设备维度下单频次超限
            int window = Math.max(1, props.getRisk().getDeviceOrderWindowMin());
            long orderCnt = trackEventMapper.selectCount(new LambdaQueryWrapper<TrackEvent>()
                    .eq(TrackEvent::getDeviceId, deviceId)
                    .eq(TrackEvent::getEvent, "order_create")
                    .ge(TrackEvent::getCreatedAt, LocalDateTime.now().minusMinutes(window)));
            if (orderCnt >= props.getRisk().getDeviceOrderRate()) {
                raise(event, "R5_DEVICE_ORDER_RATE", "设备下单频次超限", "mid", "device", deviceId);
            }
            // 规则 R6：设备黑名单命中
            if (deviceFingerprintService.isBlacklisted(deviceId)) {
                raise(event, "R6_DEVICE_BLACKLIST", "设备黑名单", "high", "device", deviceId);
            }
        }
    }

    private long parseAmount(String ext) {
        if (ext == null || ext.isBlank()) {
            return 0L;
        }
        try {
            // 兼容两种形式：纯数字（分）或 JSON {"amount":123}
            if (ext.trim().startsWith("{")) {
                com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(ext);
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
