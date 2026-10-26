package com.idlefish.trade.analytics.narrative;

import com.idlefish.trade.analytics.narrative.vo.MetricCardVO;
import lombok.Data;

import java.util.List;

/**
 * 经营看板评估快照（ITER-U06）：看板总评估的缓存载体，含结论卡 / 健康度 / 异常 / 贡献度。
 * 经 {@link DashboardService} 计算后写入 Redis（JSON），便于多端复用与降级。
 */
@Data
public class DashboardEvaluation {

    private List<MetricCardVO> cards;
    private List<AnomalyDetector.Anomaly> anomalies;
    private List<ContributionAnalyzer.Contribution> contribution;
    private HealthScoreCalculator.HealthScore health;
    private long generatedAt;
}
