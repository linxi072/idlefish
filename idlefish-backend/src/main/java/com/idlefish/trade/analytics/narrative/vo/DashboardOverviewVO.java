package com.idlefish.trade.analytics.narrative.vo;

import com.idlefish.trade.analytics.narrative.AnomalyDetector;
import com.idlefish.trade.analytics.narrative.HealthScoreCalculator;

import java.util.List;

/**
 * 经营看板概览响应：结论卡 + 健康度 + 异常列表。
 *
 * @param cards       结论卡列表（首屏「一句话结论」）
 * @param health     经营健康度评分
 * @param anomalies  异常波动列表（环比）
 * @param generatedAt 数据生成时间（epoch millis）
 */
public record DashboardOverviewVO(List<MetricCardVO> cards,
                                  HealthScoreCalculator.HealthScore health,
                                  List<AnomalyDetector.Anomaly> anomalies,
                                  long generatedAt) {
}
