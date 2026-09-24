package com.idlefish.trade.common.enums;

import lombok.Getter;

/**
 * 评价角色（PRD §6 互评）：买家评价卖家 / 卖家评价买家。
 */
@Getter
public enum ReviewRole {

    BUYER_SELLER("BUYER_SELLER", "买家评价卖家"),
    SELLER_BUYER("SELLER_BUYER", "卖家评价买家");

    private final String code;
    private final String desc;

    ReviewRole(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ReviewRole fromCode(String code) {
        for (ReviewRole r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        throw new IllegalArgumentException("unknown review role: " + code);
    }
}
