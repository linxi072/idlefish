package com.idlefish.trade.pricing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 估价纯函数单测（REQ-07）：applyMultiplier / resolveAlertAdmin / safeJson，无 Mock、无 Spring。
 */
class PricingViewsTest {

    // ===================== applyMultiplier =====================

    @Test
    void applyMultiplier_scalesByMultiplier() {
        assertEquals(120000L, PricingViews.applyMultiplier(100000L, new BigDecimal("1.2")));
    }

    @Test
    void applyMultiplier_nullMultiplierReturnsRaw() {
        assertEquals(100000L, PricingViews.applyMultiplier(100000L, null));
    }

    @Test
    void applyMultiplier_nullRawReturnsZero() {
        assertEquals(0L, PricingViews.applyMultiplier(null, new BigDecimal("1.2")));
    }

    // ===================== resolveAlertAdmin =====================

    @Test
    void resolveAlertAdmin_pricingPrecedence() {
        IdlefishProperties props = new IdlefishProperties();
        IdlefishProperties.Pricing pricing = new IdlefishProperties.Pricing();
        pricing.setAlertAdminUserId(5L);
        IdlefishProperties.Notify notify = new IdlefishProperties.Notify();
        notify.setAlertAdminUserId(7L);
        props.setPricing(pricing);
        props.setNotify(notify);
        assertEquals(5L, PricingViews.resolveAlertAdmin(props));
    }

    @Test
    void resolveAlertAdmin_fallsBackToNotify() {
        IdlefishProperties props = new IdlefishProperties();
        props.setPricing(new IdlefishProperties.Pricing());
        IdlefishProperties.Notify notify = new IdlefishProperties.Notify();
        notify.setAlertAdminUserId(7L);
        props.setNotify(notify);
        assertEquals(7L, PricingViews.resolveAlertAdmin(props));
    }

    @Test
    void resolveAlertAdmin_nullOrEmptyReturnsDefaultOne() {
        assertEquals(1L, PricingViews.resolveAlertAdmin(null));
        IdlefishProperties props = new IdlefishProperties();
        props.setPricing(new IdlefishProperties.Pricing());
        props.setNotify(new IdlefishProperties.Notify());
        assertEquals(1L, PricingViews.resolveAlertAdmin(props));
    }

    // ===================== safeJson =====================

    @Test
    void safeJson_serializesObject() {
        String json = PricingViews.safeJson(new ObjectMapper(), Map.of("a", 1, "b", "x"));
        Assertions.assertTrue(json.contains("\"a\":1"));
        Assertions.assertTrue(json.contains("\"b\":\"x\""));
    }

    @Test
    void safeJson_failureReturnsEmptyObject() {
        ObjectMapper om = mock(ObjectMapper.class);
        try {
            when(om.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));
        } catch (Exception ignored) {
        }
        assertEquals("{}", PricingViews.safeJson(om, "anything"));
    }
}
