package com.idlefish.trade.risk.service;

/**
 * 风控规则判定所需的「已解析信号」：由 {@link RiskRuleEngine} 完成计数 / 设备指纹等 IO 后填充，
 * 交由纯函数 {@link RiskRuleFunctions} 做无副作用判定，保证离线可测。
 */
public class RuleInput {

    private final String event;
    private final long amountFen;
    private final long count;        // 该规则关注的窗口内计数
    private final boolean newDevice;
    private final int bindUsers;
    private final boolean blacklisted;

    public RuleInput(String event, long amountFen, long count, boolean newDevice,
                     int bindUsers, boolean blacklisted) {
        this.event = event;
        this.amountFen = amountFen;
        this.count = count;
        this.newDevice = newDevice;
        this.bindUsers = bindUsers;
        this.blacklisted = blacklisted;
    }

    public String getEvent() {
        return event;
    }

    public long getAmountFen() {
        return amountFen;
    }

    public long getCount() {
        return count;
    }

    public boolean isNewDevice() {
        return newDevice;
    }

    public int getBindUsers() {
        return bindUsers;
    }

    public boolean isBlacklisted() {
        return blacklisted;
    }
}
