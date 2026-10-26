package com.idlefish.trade.analytics.narrative.vo;

import com.idlefish.trade.analytics.narrative.HealthScoreCalculator;

import java.util.List;

/**
 * 看板结论卡：单一指标的可读卡片（名称 + 展示值 + 语义状态 + 结论文案）。
 *
 * @param key       指标 key（与 {@code MetricDictionary} 对齐）
 * @param label     指标中文名
 * @param displayValue 展示值（已格式化，如 "¥12,345" / "3.21%"）
 * @param unit      单位
 * @param status    语义状态：good / warn / bad / neutral
 * @param conclusion 结论文案
 */
public record MetricCardVO(String key, String label, String displayValue, String unit,
                           String status, String conclusion) {
}
