package com.idlefish.trade.risk.service;

import com.idlefish.trade.risk.entity.RiskRule;

/**
 * 风控规则纯函数（F-15.1）：给定规则定义与已解析信号，返回命中等级（null 表示未命中）。
 * 不触碰数据库 / 配置文件，确保离线单测确定性。
 */
public final class RiskRuleFunctions {

    private RiskRuleFunctions() {
    }

    /**
     * @return 命中等级（low/mid/high）或 null（未命中）
     */
    public static String eval(RiskRule rule, RuleInput in) {
        if (rule == null || in == null) {
            return null;
        }
        switch (rule.getType()) {
            case RiskRuleType.FREQ_PUBLISH:
                return in.getCount() >= rule.getThreshold() ? levelOf(rule, "mid") : null;
            case RiskRuleType.REFUND_ABUSE:
                return in.getCount() >= rule.getThreshold() ? levelOf(rule, "high") : null;
            case RiskRuleType.NEW_DEVICE_PAY:
                if (!in.isNewDevice()) {
                    return null;
                }
                // 新设备 + 超阈值金额 → 高危；否则中危
                return in.getAmountFen() >= rule.getThreshold() ? "high" : levelOf(rule, "mid");
            case RiskRuleType.DEVICE_MULTI_ACCOUNT:
                if (in.getBindUsers() < rule.getThreshold()) {
                    return null;
                }
                // 达到阈值两倍视为群控 / 接码高危
                return in.getBindUsers() >= rule.getThreshold() * 2L ? "high" : levelOf(rule, "mid");
            case RiskRuleType.DEVICE_ORDER_RATE:
                return in.getCount() >= rule.getThreshold() ? levelOf(rule, "mid") : null;
            case RiskRuleType.DEVICE_BLACKLIST:
                return in.isBlacklisted() ? levelOf(rule, "high") : null;
            default:
                return null;
        }
    }

    private static String levelOf(RiskRule rule, String fallback) {
        return (rule.getLevel() == null || rule.getLevel().isBlank()) ? fallback : rule.getLevel();
    }
}
