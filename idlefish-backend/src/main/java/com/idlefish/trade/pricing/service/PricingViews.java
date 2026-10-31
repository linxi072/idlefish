package com.idlefish.trade.pricing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;

import java.math.BigDecimal;

/**
 * 估价纯函数（ITER-F03）：运营系数应用 / 告警管理员解析 / JSON 安全序列化。
 * 无副作用、无 Mapper 依赖，便于离线单测（REQ-07）。
 *
 * <p>纯函数必须保持「入参 → 出参」确定性，不读库、不落地、不依赖 Spring 上下文；
 * 任何需要 Mapper 或通知等副作用的逻辑都留在 {@link PricingService} 或下沉 {@link PricingStore}。
 */
public final class PricingViews {

    private PricingViews() {
    }

    /**
     * 基准价应用运营系数：round(raw × multiplier)。multiplier 为 null 时原样返回 raw（视为系数 1.0）。
     * raw 本身为 null 时返回 0（调用方据此降级，REQ-05）。
     */
    public static long applyMultiplier(Long raw, BigDecimal multiplier) {
        if (raw == null) {
            return 0L;
        }
        if (multiplier == null) {
            return raw;
        }
        return Math.round(raw * multiplier.doubleValue());
    }

    /**
     * 解析告警管理员 userId：优先 {@code pricing.alertAdminUserId}，其次 {@code notify.alertAdminUserId}，兜底 1L。
     * props 为 null 或两处均未配置时返回 1L（保证异常低价送审必有接收人，REQ-10）。
     */
    public static long resolveAlertAdmin(IdlefishProperties props) {
        if (props != null && props.getPricing() != null
                && props.getPricing().getAlertAdminUserId() != null) {
            return props.getPricing().getAlertAdminUserId();
        }
        if (props != null && props.getNotify() != null
                && props.getNotify().getAlertAdminUserId() != null) {
            return props.getNotify().getAlertAdminUserId();
        }
        return 1L;
    }

    /** 安全 JSON 序列化：失败返回 "{}"，不抛异常（审计留痕允许降级，REQ-08）。 */
    public static String safeJson(ObjectMapper om, Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
