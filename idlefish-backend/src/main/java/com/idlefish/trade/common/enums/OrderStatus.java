package com.idlefish.trade.common.enums;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import lombok.Getter;

/**
 * 订单状态机（PRD §4.3，6 态）。
 * pending_pay → paid → pending_ship → shipping → completed → closed
 */
@Getter
public enum OrderStatus {

    PENDING_PAY("pending_pay", "待支付"),
    PAID("paid", "已支付(托管)"),
    PENDING_SHIP("pending_ship", "待发货"),
    SHIPPING("shipping", "运输中/待收货"),
    COMPLETED("completed", "已完成(已结算)"),
    CLOSED("closed", "已关闭");

    private final String code;
    private final String desc;

    OrderStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus fromCode(String code) {
        for (OrderStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new BizException(Code.PARAM_INVALID, "unknown order status: " + code);
    }
}
