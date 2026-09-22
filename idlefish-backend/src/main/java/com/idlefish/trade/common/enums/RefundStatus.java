package com.idlefish.trade.common.enums;

import lombok.Getter;

/**
 * 退款状态机（PRD §4.4，7 态）。
 */
@Getter
public enum RefundStatus {

    APPLY("apply", "退款申请中"),
    WAIT_SELLER("wait_seller", "待卖家处理"),
    PLATFORM("platform", "平台介入中"),
    REFUNDING("refunding", "退款处理中"),
    REFUNDED("refunded", "已退款"),
    REJECTED("rejected", "已拒绝"),
    CANCELED("canceled", "已撤销");

    private final String code;
    private final String desc;

    RefundStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RefundStatus fromCode(String code) {
        for (RefundStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("unknown refund status: " + code);
    }
}
