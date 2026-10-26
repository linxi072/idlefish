package com.idlefish.trade.analytics.narrative;

import com.idlefish.trade.analytics.mapper.AnalyticsMapper;
import com.idlefish.trade.analytics.narrative.vo.DashboardOverviewVO;
import com.idlefish.trade.analytics.narrative.vo.MetricCardVO;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 经营数据可视化叙事服务（ITER-U06）：在 F-15.5 运营 BI 之上构建「叙事层」。
 * <p>复用 {@link AnalyticsMapper} 只读聚合，经纯函数（MetricDictionary / HealthScoreCalculator /
 * AnomalyDetector / ContributionAnalyzer）派生结论卡、健康度、异常与贡献度，并以 Redis 缓存聚合结果。
 * 缓存为 best-effort：Redis 不可用时自动降级为实时计算，保证看板接口不崩溃。</p>
 */
@Service
public class DashboardService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final AnalyticsMapper analyticsMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DashboardService(AnalyticsMapper analyticsMapper, StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper) {
        this.analyticsMapper = analyticsMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 看板总评估（带 Redis 缓存，best-effort）。 */
    public DashboardEvaluation evaluate(int days) {
        int d = clampDays(days);
        String key = "dashboard:eval:" + d;
        DashboardEvaluation cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        DashboardEvaluation ev = compute(d);
        writeCache(key, ev);
        return ev;
    }

    /** 概览：结论卡 + 健康度 + 异常。 */
    public DashboardOverviewVO overview(int days) {
        DashboardEvaluation ev = evaluate(days);
        return new DashboardOverviewVO(ev.getCards(), ev.getHealth(), ev.getAnomalies(), ev.getGeneratedAt());
    }

    /** 异常波动（环比）。 */
    public List<AnomalyDetector.Anomaly> anomalies(int days) {
        return evaluate(days).getAnomalies();
    }

    /** 维度贡献度（按品类 GMV 占比 + 负向下钻）。 */
    public List<ContributionAnalyzer.Contribution> contribution(int days, String dimension) {
        if (!"category".equals(dimension)) {
            throw new BizException(Code.PARAM_INVALID, "unsupported dimension: " + dimension);
        }
        int d = clampDays(days);
        LocalDateTime now = LocalDateTime.now();
        List<ContributionAnalyzer.Item> cur = toItems(analyticsMapper.categoryGmv(now.minusDays(d)));
        List<ContributionAnalyzer.Item> prev = toItems(analyticsMapper.categoryGmv(now.minusDays(2 * d)));
        return ContributionAnalyzer.analyze(cur, 5);
    }

    // ===== 计算 =====

    private DashboardEvaluation compute(int days) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> cur = analyticsMapper.overviewTotals(now.minusDays(days));
        Map<String, Object> prev = analyticsMapper.overviewTotals(now.minusDays(2 * days));

        long gmvCur = toLong(cur.get("gmv"));
        long gmvPrev = toLong(prev.get("gmv"));
        long paidCur = toLong(cur.get("paidOrderCount"));
        long paidPrev = toLong(prev.get("paidOrderCount"));
        long refundCur = toLong(cur.get("refundOrderCount"));
        long refundPrev = toLong(prev.get("refundOrderCount"));

        double refundRateCur = safeRate(refundCur, paidCur);
        double refundRatePrev = safeRate(refundPrev, paidPrev);
        double gmvGrowth = safeGrowth(gmvCur, gmvPrev);
        double paidGrowth = safeGrowth(paidCur, paidPrev);
        double aovCur = safeRate(gmvCur, paidCur);   // 客单价（元）= GMV元 / 订单数
        double aovPrev = safeRate(gmvPrev, paidPrev);
        double aovGrowth = safeGrowth(aovCur, aovPrev);

        // 结论卡（首屏一句话结论）
        List<MetricCardVO> cards = new ArrayList<>();
        cards.add(card("gmv", gmvCur / 100.0, "元"));
        cards.add(card("paidOrderCount", (double) paidCur, "笔"));
        cards.add(card("refundRate", refundRateCur, ""));
        cards.add(card("avgOrderValue", aovCur, "元"));
        cards.add(card("userCount", (double) analyticsMapper.userCount(), "人"));
        cards.add(card("itemOnsaleCount", (double) analyticsMapper.itemOnsaleCount(), "件"));

        HealthScoreCalculator.HealthScore health =
                HealthScoreCalculator.calc(refundRateCur, gmvGrowth);
        List<AnomalyDetector.Anomaly> anomalies = AnomalyDetector.detect(
                refundRateCur, refundRatePrev, gmvGrowth, paidGrowth, aovGrowth);

        // 品类 GMV 贡献度（当前周期）
        List<ContributionAnalyzer.Item> items = toItems(analyticsMapper.categoryGmv(now.minusDays(days)));
        List<ContributionAnalyzer.Contribution> contribution = ContributionAnalyzer.analyze(items, 5);

        DashboardEvaluation ev = new DashboardEvaluation();
        ev.setCards(cards);
        ev.setHealth(health);
        ev.setAnomalies(anomalies);
        ev.setContribution(contribution);
        ev.setGeneratedAt(System.currentTimeMillis());
        return ev;
    }

    private MetricCardVO card(String key, double value, String unit) {
        return new MetricCardVO(key, MetricDictionary.def(key).label(),
                MetricDictionary.format(value, unit), unit,
                MetricDictionary.judge(key, value), MetricDictionary.conclusion(key, value, unit));
    }

    private List<ContributionAnalyzer.Item> toItems(List<Map<String, Object>> rows) {
        List<ContributionAnalyzer.Item> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String label = String.valueOf(r.get("categoryName"));
            double value = toLong(r.get("gmv"));
            out.add(new ContributionAnalyzer.Item(label, value));
        }
        return out;
    }

    // ===== 缓存（best-effort）=====

    private DashboardEvaluation readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, DashboardEvaluation.class);
        } catch (Exception e) {
            return null; // 缓存读取失败：降级实时计算
        }
    }

    private void writeCache(String key, DashboardEvaluation ev) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(ev), CACHE_TTL);
        } catch (Exception ignore) {
            // 缓存写入失败：忽略，下次命中 miss 重新计算
        }
    }

    // ===== 辅助 =====

    private int clampDays(int days) {
        if (days < MIN_DAYS) {
            return MIN_DAYS;
        }
        if (days > MAX_DAYS) {
            return MAX_DAYS;
        }
        return days;
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    /** 比率 a/b；b<=0 返回 0 避免除零。 */
    private static double safeRate(long a, long b) {
        if (b <= 0) {
            return 0.0;
        }
        return (double) a / b;
    }

    /** 环比增长率 (cur-prev)/prev；prev<=0 返回 0 避免除零/伪增长。 */
    private static double safeGrowth(double cur, double prev) {
        if (prev <= 0) {
            return 0.0;
        }
        return (cur - prev) / prev;
    }
}
