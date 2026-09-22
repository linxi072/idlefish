package com.idlefish.trade.common.enums;

import lombok.Getter;

/**
 * 支付单状态。
 */
@Getter
public enum PayStatus {

    WAIT("wait", "待支付"),
    SUCCESS("success", "支付成功(已托管)"),
    CLOSED("closed", "已关闭"),
    REFUNDED("refunded", "已退款");

    private final String code;
    private final String desc;

    PayStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PayStatus fromCode(String code) {
        for (PayStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("unknown pay status: " + code);
    }
}
