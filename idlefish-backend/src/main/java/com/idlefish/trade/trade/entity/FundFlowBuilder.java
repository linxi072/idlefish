package com.idlefish.trade.trade.entity;

/**
 * 资金流水构造器：消除各业务（支付/退款/提现/结算）中重复的 new FundFlow() + 6 次 setter 样板。
 * 仅封装字段装配，落库仍由各调用方的 FundFlowMapper.insert 执行，不改变任何业务语义与字段。
 */
public class FundFlowBuilder {

    private final FundFlow ff = new FundFlow();

    private FundFlowBuilder() {
    }

    public static FundFlowBuilder of(String bizNo, Long userId, String direction, String type, Long amount) {
        FundFlowBuilder b = new FundFlowBuilder();
        b.ff.setBizNo(bizNo);
        b.ff.setUserId(userId);
        b.ff.setDirection(direction);
        b.ff.setType(type);
        b.ff.setAmount(amount);
        return b;
    }

    public FundFlowBuilder balanceAfter(Long balanceAfter) {
        ff.setBalanceAfter(balanceAfter);
        return this;
    }

    public FundFlow build() {
        return ff;
    }
}
