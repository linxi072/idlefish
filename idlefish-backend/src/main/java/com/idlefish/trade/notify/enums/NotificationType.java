package com.idlefish.trade.notify.enums;

import lombok.Getter;

/**
 * 通知类型（事件分类）。code 持久化到 t_notification.type，desc 用于展示与日志。
 */
@Getter
public enum NotificationType {

    ORDER_PAID("order_paid", "订单支付"),
    ORDER_SHIPPED("order_shipped", "卖家发货"),
    ORDER_CONFIRMED("order_confirmed", "确认收货"),
    ORDER_CLOSED("order_closed", "订单关闭"),
    REFUND_APPLY("refund_apply", "退款申请"),
    REFUND_SUCCESS("refund_success", "退款成功"),
    REFUND_REJECTED("refund_rejected", "退款被拒"),
    REFUND_CANCELED("refund_canceled", "退款撤销"),
    REFUND_PLATFORM("refund_platform", "平台介入"),
    ITEM_APPROVED("item_approved", "商品过审"),
    ITEM_REJECTED("item_rejected", "商品驳回"),
    REMIND_SHIP("remind_ship", "发货提醒"),
    SETTLEMENT_SUCCESS("settlement_success", "结算到账"),
    WITHDRAW_APPLY("withdraw_apply", "提现申请"),
    WITHDRAW_APPROVE("withdraw_approve", "提现通过"),
    WITHDRAW_REJECT("withdraw_reject", "提现驳回"),
    COUPON_CLAIMED("coupon_claimed", "优惠券领取"),
    POINT_EARNED("point_earned", "积分到账"),
    DISPUTE_CREATED("dispute_created", "维权发起"),
    DISPUTE_PLATFORM("dispute_platform", "维权平台介入"),
    DISPUTE_RESOLVED("dispute_resolved", "维权裁决"),
    INSPECTION_CREATED("inspection_created", "验货送检"),
    INSPECTION_RECEIVED("inspection_received", "机构收件"),
    INSPECTION_RESULT("inspection_result", "验货结果"),
    INSPECTION_EXCEPTION("inspection_exception", "验货异常"),
    SYSTEM_ALERT("system_alert", "系统告警");

    private final String code;
    private final String desc;

    NotificationType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
