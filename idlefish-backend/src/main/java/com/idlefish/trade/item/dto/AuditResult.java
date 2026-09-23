package com.idlefish.trade.item.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 内容审核结果：机审/AI 审核统一返回。
 * - pass：是否通过（false 时业务侧应驳回或转人工）
 * - suggestion：pass / block / review
 * - reason：不通过原因（便于驳回说明）
 */
@Data
public class AuditResult implements Serializable {

    private boolean pass;
    private String suggestion;
    private String reason;

    public static AuditResult pass() {
        AuditResult r = new AuditResult();
        r.setPass(true);
        r.setSuggestion("pass");
        return r;
    }

    public static AuditResult reject(String reason) {
        AuditResult r = new AuditResult();
        r.setPass(false);
        r.setSuggestion("block");
        r.setReason(reason);
        return r;
    }
}
