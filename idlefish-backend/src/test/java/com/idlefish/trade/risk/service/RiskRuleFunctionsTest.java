package com.idlefish.trade.risk.service;

import com.idlefish.trade.risk.entity.RiskRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 风控规则纯函数单测（F-15.1）：无数据库 / 无 Spring，验证 R1~R6 判定确定性。
 */
class RiskRuleFunctionsTest {

    private RiskRule rule(String type, long threshold, String level) {
        RiskRule r = new RiskRule();
        r.setType(type);
        r.setThreshold(threshold);
        r.setLevel(level);
        return r;
    }

    @Test
    @DisplayName("R1 高频发布：达到阈值命中 mid，未达返回 null")
    void freqPublish() {
        RiskRule r = rule(RiskRuleType.FREQ_PUBLISH, 5, "mid");
        assertEquals("mid", RiskRuleFunctions.eval(r, new RuleInput("publish", 0, 5, false, 0, false)));
        assertEquals("mid", RiskRuleFunctions.eval(r, new RuleInput("publish", 0, 9, false, 0, false)));
        assertNull(RiskRuleFunctions.eval(r, new RuleInput("publish", 0, 4, false, 0, false)));
    }

    @Test
    @DisplayName("R2 退款滥用：达到阈值命中 high")
    void refundAbuse() {
        RiskRule r = rule(RiskRuleType.REFUND_ABUSE, 3, "high");
        assertEquals("high", RiskRuleFunctions.eval(r, new RuleInput("refund_apply", 0, 3, false, 0, false)));
        assertNull(RiskRuleFunctions.eval(r, new RuleInput("refund_apply", 0, 2, false, 0, false)));
    }

    @Test
    @DisplayName("R3 新设备大额：新设备+超阈值金额 high，新设备+低额 mid，非新设备 null")
    void newDevicePay() {
        RiskRule r = rule(RiskRuleType.NEW_DEVICE_PAY, 100, "mid");
        assertEquals("high", RiskRuleFunctions.eval(r, new RuleInput("pay", 200, 0, true, 0, false)));
        assertEquals("mid", RiskRuleFunctions.eval(r, new RuleInput("pay", 50, 0, true, 0, false)));
        assertNull(RiskRuleFunctions.eval(r, new RuleInput("pay", 200, 0, false, 0, false)));
    }

    @Test
    @DisplayName("R4 同设备多账号：达两倍阈值 high，达一倍 mid，未达 null")
    void deviceMultiAccount() {
        RiskRule r = rule(RiskRuleType.DEVICE_MULTI_ACCOUNT, 3, "mid");
        assertEquals("high", RiskRuleFunctions.eval(r, new RuleInput("login", 0, 0, false, 6, false)));
        assertEquals("mid", RiskRuleFunctions.eval(r, new RuleInput("login", 0, 0, false, 3, false)));
        assertNull(RiskRuleFunctions.eval(r, new RuleInput("login", 0, 0, false, 2, false)));
    }

    @Test
    @DisplayName("R5 设备下单频次：达到阈值命中 mid")
    void deviceOrderRate() {
        RiskRule r = rule(RiskRuleType.DEVICE_ORDER_RATE, 10, "mid");
        assertEquals("mid", RiskRuleFunctions.eval(r, new RuleInput("order_create", 0, 10, false, 0, false)));
        assertNull(RiskRuleFunctions.eval(r, new RuleInput("order_create", 0, 9, false, 0, false)));
    }

    @Test
    @DisplayName("R6 设备黑名单：命中 high，未命中 null")
    void deviceBlacklist() {
        RiskRule r = rule(RiskRuleType.DEVICE_BLACKLIST, 0, "high");
        assertEquals("high", RiskRuleFunctions.eval(r, new RuleInput("pay", 0, 0, false, 0, true)));
        assertNull(RiskRuleFunctions.eval(r, new RuleInput("pay", 0, 0, false, 0, false)));
    }

    @Test
    @DisplayName("空入参安全返回 null")
    void nullSafe() {
        assertNull(RiskRuleFunctions.eval(null, new RuleInput("pay", 0, 0, false, 0, false)));
        assertNull(RiskRuleFunctions.eval(rule(RiskRuleType.DEVICE_BLACKLIST, 0, "high"), null));
    }
}
