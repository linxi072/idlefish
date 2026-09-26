package com.idlefish.trade.risk.service;

/**
 * 风控规则类型常量（F-15.1）。与 {@code t_risk_rule.type} 列一一对应，由 {@link RiskRuleFunctions} 分派判定。
 */
public final class RiskRuleType {

    /** 用户高频发布（原 R1）。 */
    public static final String FREQ_PUBLISH = "FREQ_PUBLISH";
    /** 高频退款申请（原 R2）。 */
    public static final String REFUND_ABUSE = "REFUND_ABUSE";
    /** 新设备 + 大额交易（原 R3）。 */
    public static final String NEW_DEVICE_PAY = "NEW_DEVICE_PAY";
    /** 同设备绑定多账号（原 R4）。 */
    public static final String DEVICE_MULTI_ACCOUNT = "DEVICE_MULTI_ACCOUNT";
    /** 设备维度下单频次超限（原 R5）。 */
    public static final String DEVICE_ORDER_RATE = "DEVICE_ORDER_RATE";
    /** 设备黑名单命中（原 R6）。 */
    public static final String DEVICE_BLACKLIST = "DEVICE_BLACKLIST";

    private RiskRuleType() {
    }
}
