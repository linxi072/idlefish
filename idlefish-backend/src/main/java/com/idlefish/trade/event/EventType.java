package com.idlefish.trade.event;

import lombok.Getter;

import java.util.Arrays;

/**
 * 领域事件类型注册表（ITER-T06 / REQ-01/02）。
 * <p>
 * 每类事件携带：
 * <ul>
 *   <li>{@code code}：事件类型唯一标识（大写下划线，对外与 MQ topic tag 一致）；</li>
 *   <li>{@code version}：schema 版本号（REQ-02 版本化，负载结构不兼容变更时递增）；</li>
 *   <li>{@code persisted}：是否写入事件存储 {@code t_domain_event}（REQ-09 ≥90 天可回放）。
 *       默认全部持久化；未来高频低价值事件（如浏览埋点）可置 false 控制存储增长。</li>
 * </ul>
 * <p>金额类负载字段单位一律「分」。</p>
 */
@Getter
public enum EventType {

    // ===== 订单域（REQ-03）=====
    /** 订单已创建（买家下单成功）。 */
    ORDER_CREATED("订单已创建", 1, true),
    /** 订单已支付（买家付款成功）。 */
    ORDER_PAID("订单已支付", 1, true),
    /** 订单已发货（卖家发货）。 */
    ORDER_SHIPPED("订单已发货", 1, true),
    /** 订单已完成（确认收货 / 自动确认）。 */
    ORDER_COMPLETED("订单已完成", 1, true),
    /** 订单已取消（买家取消 / 超时关单）。 */
    ORDER_CANCELLED("订单已取消", 1, true),
    /** 订单已退款（全额退款完成）。 */
    ORDER_REFUNDED("订单已退款", 1, true),

    // ===== 资金域（REQ-04，金额单位「分」）=====
    /** 支付成功（支付回调验签通过后）。 */
    PAYMENT_SUCCEEDED("支付成功", 1, true),
    /** 分账已执行（确认收货后卖家结算）。 */
    PROFIT_SHARE_EXECUTED("分账已执行", 1, true),
    /** 提现已完成（出款到账）。 */
    WITHDRAWAL_COMPLETED("提现已完成", 1, true),
    /** 退款已执行（原路退回到账）。 */
    REFUND_EXECUTED("退款已执行", 1, true),

    // ===== 商品域 =====
    /** 商品已发布（上架成功）。 */
    ITEM_PUBLISHED("商品已发布", 1, true),
    /** 商品已售罄（交易成交后下架）。 */
    ITEM_SOLD_OUT("商品已售罄", 1, true),

    // ===== 用户域 =====
    /** 用户已注册（新用户完成微信登录注册）。 */
    USER_REGISTERED("用户已注册", 1, true),
    /** 信用分已变更（履约 / 风控 / 违规扣分）。 */
    CREDIT_SCORE_CHANGED("信用分已变更", 1, true),

    // ===== 维权 / 履约域 =====
    /** 维权工单已开启（买家发起维权）。 */
    DISPUTE_OPENED("维权工单已开启", 1, true),
    /** 维权工单已办结（协商 / 平台裁决）。 */
    DISPUTE_RESOLVED("维权工单已办结", 1, true),
    /** 验货已完成（鉴定验货出具报告）。 */
    INSPECTION_COMPLETED("验货已完成", 1, true),
    /** 客服工单已开启（用户提交工单）。 */
    WORKORDER_OPENED("客服工单已开启", 1, true),
    /** 客服工单已解决（客服标记解决）。 */
    WORKORDER_RESOLVED("客服工单已解决", 1, true),
    /** 客服工单已关闭（用户确认或管理员关闭）。 */
    WORKORDER_CLOSED("客服工单已关闭", 1, true);

    /** 事件类型说明。 */
    private final String desc;

    /** schema 版本号（REQ-02）。 */
    private final int version;

    /** 是否持久化到事件存储（REQ-09 可回放）。 */
    private final boolean persisted;

    EventType(String desc, int version, boolean persisted) {
        this.desc = desc;
        this.version = version;
        this.persisted = persisted;
    }

    /** 按 code 解析；未知 code 返回 {@code null}（调用方按 EVENT_TYPE_UNSUPPORTED 处理）。 */
    public static EventType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equals(code))
                .findFirst()
                .orElse(null);
    }
}
