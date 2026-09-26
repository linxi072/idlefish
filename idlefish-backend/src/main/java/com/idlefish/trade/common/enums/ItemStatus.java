package com.idlefish.trade.common.enums;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import lombok.Getter;

/**
 * 商品状态机（PRD §4.2，7 态）。
 * draft → pending_review → onsale → {locked → sold | off_shelf | rejected}
 */
@Getter
public enum ItemStatus {

    DRAFT("draft", "草稿"),
    PENDING_REVIEW("pending_review", "待审核"),
    ONSALE("onsale", "在售"),
    LOCKED("locked", "已锁定(下单锁库存)"),
    SOLD("sold", "已售出"),
    REJECTED("rejected", "审核驳回"),
    OFF_SHELF("off_shelf", "已下架/删除");

    private final String code;
    private final String desc;

    ItemStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ItemStatus fromCode(String code) {
        for (ItemStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new BizException(Code.PARAM_INVALID, "unknown item status: " + code);
    }
}
