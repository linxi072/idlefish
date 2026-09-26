package com.idlefish.trade.risk.service;

/**
 * 规则引擎的一次命中结果，交给 {@link RiskEngine} 落地为 RiskEvent。
 */
public class RuleHit {

    private final String code;
    private final String name;
    private final String level;
    private final String bizType;
    private final String bizId;

    public RuleHit(String code, String name, String level, String bizType, String bizId) {
        this.code = code;
        this.name = name;
        this.level = level;
        this.bizType = bizType;
        this.bizId = bizId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getLevel() {
        return level;
    }

    public String getBizType() {
        return bizType;
    }

    public String getBizId() {
        return bizId;
    }
}
