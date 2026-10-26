package com.idlefish.trade.analytics.narrative;

import com.idlefish.trade.analytics.narrative.ContributionAnalyzer.Attribution;
import com.idlefish.trade.analytics.narrative.ContributionAnalyzer.Contribution;
import com.idlefish.trade.analytics.narrative.ContributionAnalyzer.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 经营叙事层纯函数单测（离线、无 Spring、无 MySQL）：健康度评分 / 异常检测 / 贡献度 / 指标字典。
 */
class NarrativeCalculatorTest {

    @Test
    void healthScore_healthy() {
        HealthScoreCalculator.HealthScore s = HealthScoreCalculator.calc(0.01, 0.1);
        assertEquals(90, s.score());
        assertEquals("健康", s.level());
    }

    @Test
    void healthScore_alert() {
        HealthScoreCalculator.HealthScore s = HealthScoreCalculator.calc(0.10, -0.5);
        // refundPenalty=40, gmvPenalty=30 -> 30, 预警
        assertEquals(30, s.score());
        assertEquals("预警", s.level());
    }

    @Test
    void anomalyDetect_flags_all_when_deteriorating() {
        List<AnomalyDetector.Anomaly> list = AnomalyDetector.detect(0.06, 0.02, -0.3, -0.3, -0.3);
        assertEquals(4, list.size());
        assertTrue(list.stream().anyMatch(a -> "refundRate".equals(a.metric()) && "warn".equals(a.severity())));
        assertTrue(list.stream().anyMatch(a -> "gmv".equals(a.metric()) && "bad".equals(a.severity())));
        assertTrue(list.stream().anyMatch(a -> "paidOrderCount".equals(a.metric()) && "bad".equals(a.severity())));
        assertTrue(list.stream().anyMatch(a -> "avgOrderValue".equals(a.metric()) && "warn".equals(a.severity())));
    }

    @Test
    void anomalyDetect_empty_when_stable() {
        List<AnomalyDetector.Anomaly> list = AnomalyDetector.detect(0.02, 0.02, 0.05, 0.05, 0.0);
        assertTrue(list.isEmpty());
    }

    @Test
    void contribution_analyze_shares_and_top() {
        List<Contribution> out = ContributionAnalyzer.analyze(
                List.of(new Item("A", 50), new Item("B", 30), new Item("C", 20)), 2);
        assertEquals(3, out.size());
        assertEquals(0.5, out.get(0).share(), 1e-9);
        assertEquals(0.3, out.get(1).share(), 1e-9);
        assertEquals(0.2, out.get(2).share(), 1e-9);
        assertEquals(0.8, out.get(1).cumulativeShare(), 1e-9);
        assertTrue(out.get(0).top());
        assertTrue(out.get(1).top());
        assertFalse(out.get(2).top());
    }

    @Test
    void contribution_negative_attrition_ranks_most_negative_first() {
        List<Attribution> out = ContributionAnalyzer.negativeContributors(
                List.of(new Item("A", 50), new Item("B", 30)),
                List.of(new Item("A", 100), new Item("B", 100)), 1);
        assertEquals(1, out.size());
        assertEquals("B", out.get(0).label());
        assertEquals(-70.0, out.get(0).delta(), 1e-9);
    }

    @Test
    void metricDictionary_judge_refundRate_bands() {
        assertEquals("good", MetricDictionary.judge("refundRate", 0.02));
        assertEquals("warn", MetricDictionary.judge("refundRate", 0.05));
        assertEquals("bad", MetricDictionary.judge("refundRate", 0.10));
        assertEquals("good", MetricDictionary.judge("gmv", 1000));
    }

    @Test
    void metricDictionary_conclusion_contains_label() {
        String c = MetricDictionary.conclusion("refundRate", 0.10, "");
        assertTrue(c.contains("退款率"));
        assertTrue(c.contains("预警"));
    }
}
