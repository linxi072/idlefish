package com.idlefish.trade.meta.vo;

import java.util.Map;

/**
 * 设计令牌（Design Token）：将品牌 / 语义 / 间距等设计决策抽象为单一数据源，
 * 多端按 {@code platforms} 映射落地（Web=Element Plus CSS 变量，小程序=rpx 常量），保证视觉一致。
 *
 * @param token     令牌名（语义化，如 color-primary）
 * @param value     规范值（Web 端权威值）
 * @param platforms 各端映射（key=端，value=该端落地表达式）
 * @param usage     使用场景说明
 */
public record DesignTokenVO(String token, String value, Map<String, String> platforms, String usage) {
}
