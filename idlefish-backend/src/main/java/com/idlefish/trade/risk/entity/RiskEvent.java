package com.idlefish.trade.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;

import java.io.Serializable;

/**
 * 风控事件：风控引擎命中规则后产生，用于人工核查或自动处置。
 */
@TableName("t_risk_event")
public class RiskEvent extends BaseEntity implements Serializable {

    private Long userId;
    private String ruleCode;
    private String ruleName;
    private String level;      // low / mid / high
    private String bizType;    // 关联业务（order/refund/item...）
    private String bizId;
    private String status = "open"; // open / handled / ignored

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
