package com.idlefish.trade.analytics;

import com.idlefish.trade.analytics.vo.AnalyticsFunnelStage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-15.5 纯函数离线单测：派生指标计算与漏斗组装（无 DB / 无 Spring）。
 */
class AnalyticsCalculatorTest {

    @Test
    void refundRate_zeroPaid_returnsZero() {
        assertEquals(0.0, AnalyticsCalculator.refundRate(0, 0));
        assertEquals(0.0, AnalyticsCalculator.refundRate(10, 0));
    }

    @Test
    void refundRate_normal() {
        assertEquals(0.1, AnalyticsCalculator.refundRate(10, 100), 1e-9);
        assertEquals(9.0 / 342.0, AnalyticsCalculator.refundRate(9, 342), 1e-9);
    }

    @Test
    void avgOrderValue_zeroPaid_returnsZero() {
        assertEquals(0L, AnalyticsCalculator.avgOrderValue(10000, 0));
    }

    @Test
    void avgOrderValue_normal() {
        assertEquals(2500L, AnalyticsCalculator.avgOrderValue(10000, 4));
        assertEquals(37602L, AnalyticsCalculator.avgOrderValue(12860000, 342));
    }

    @Test
    void stepRate_and_overallRate() {
        assertEquals(0.0, AnalyticsCalculator.stepRate(0, 50));
        assertEquals(0.5, AnalyticsCalculator.stepRate(100, 50), 1e-9);
        assertEquals(0.25, AnalyticsCalculator.overallRate(200, 50), 1e-9);
    }

    @Test
    void buildFunnel_ordersStagesAndRates() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("view_item", 1000L);
        counts.put("search", 400L);
        counts.put("favorite", 200L);
        counts.put("order_create", 120L);
        counts.put("pay", 80L);

        List<AnalyticsFunnelStage> funnel = AnalyticsCalculator.buildFunnel(counts);
        assertEquals(5, funnel.size());

        AnalyticsFunnelStage top = funnel.get(0);
        assertEquals("商品浏览", top.getStage());
        assertEquals(1000L, top.getCount());
        assertEquals(1.0, top.getStepRate(), 1e-9);
        assertEquals(1.0, top.getOverallRate(), 1e-9);

        AnalyticsFunnelStage search = funnel.get(1);
        assertEquals(400L, search.getCount());
        assertEquals(0.4, search.getStepRate(), 1e-9);
        assertEquals(0.4, search.getOverallRate(), 1e-9);

        AnalyticsFunnelStage pay = funnel.get(4);
        assertEquals("支付", pay.getStage());
        assertEquals(80L, pay.getCount());
        assertEquals(80.0 / 120.0, pay.getStepRate(), 1e-9);
        assertEquals(0.08, pay.getOverallRate(), 1e-9);
    }

    @Test
    void buildFunnel_missingEventsDefaultZero() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("view_item", 500L);
        // 其余事件缺失 → 0

        List<AnalyticsFunnelStage> funnel = AnalyticsCalculator.buildFunnel(counts);
        assertEquals(5, funnel.size());
        assertEquals(500L, funnel.get(0).getCount());
        for (int i = 1; i < funnel.size(); i++) {
            assertEquals(0L, funnel.get(i).getCount());
            assertEquals(0.0, funnel.get(i).getStepRate(), 1e-9);
            assertEquals(0.0, funnel.get(i).getOverallRate(), 1e-9);
        }
        assertTrue(funnel.get(0).getStepRate() > 0);
    }
}
