package com.idlefish.trade.member;

import java.util.ArrayList;
import java.util.List;

/**
 * 刷分识别纯函数（ITER-F07 / REQ-03）：命中刷分特征的成长值发放请求一律拒绝发放，并冻结账户发放通道。
 * <p>
 * 与 {@link GrowthValueCalculator} 同范式：无副作用、不访问 DB / Spring 上下文，离线可单测（REQ-06）。
 * 识别策略对齐 PRD 边界场景「刷分对抗」三要素：<b>关联账户 + 频次阈值 + 金额门槛</b>。
 * </p>
 * <p>规则判定为「从严」：任一特征命中即拦截；同一请求可命中多条规则，全部返回便于运营归因。</p>
 */
public final class GrowthAntiFraud {

    private GrowthAntiFraud() {
    }

    /** 规则编码（持久化到流水 remark / 审计，便于对账与规则迭代）。 */
    public static final String RULE_LINKED_ACCOUNT = "LINKED_ACCOUNT";
    public static final String RULE_FREQUENCY = "FREQUENCY";
    public static final String RULE_AMOUNT_THRESHOLD = "AMOUNT_THRESHOLD";
    public static final String RULE_ALREADY_FROZEN = "ALREADY_FROZEN";

    /**
     * 识别输入（纯数据快照，由调用方从 DB / 风控结果装配）。
     *
     * @param linkedAccountCount  关联账户数（同设备 / 同 IP 绑定的账号数）
     * @param maxLinkedAccounts   关联账户数阈值，超过即视为团伙刷分
     * @param earnCountInWindow   窗口内该账户已获得的成长值发放次数
     * @param maxEarnInWindow     窗口内发放次数阈值，超过即视为频次刷分
     * @param amountFen           本次交易金额（分）；非交易场景传 0 表示跳过金额门槛判定
     * @param minAmountFen        金额门槛（分）：低于该金额的交易不发成长值，防 1 分钱刷量
     * @param accountFrozen       账户发放通道是否已冻结
     */
    public record Input(int linkedAccountCount, int maxLinkedAccounts,
                        int earnCountInWindow, int maxEarnInWindow,
                        long amountFen, long minAmountFen,
                        boolean accountFrozen) {
        public static Input empty() {
            return new Input(0, 0, 0, 0, 0L, 0L, false);
        }
    }

    /**
     * 识别结果。
     *
     * @param blocked  true=不予发放
     * @param freeze   true=需冻结该账户的成长值发放通道（REQ-03）
     * @param ruleCodes 命中的规则编码（可多条）
     */
    public record Decision(boolean blocked, boolean freeze, List<String> ruleCodes) {
        public static Decision pass() {
            return new Decision(false, false, List.of());
        }

        public String ruleCodesText() {
            return String.join(",", ruleCodes);
        }
    }

    /**
     * 判定：任一特征命中即 blocked；命中刷分特征（关联账户 / 频次 / 金额门槛）时同步要求冻结通道；
     * 账户已冻结时直接 blocked 且要求保持冻结（fail-closed，REQ-03）。
     */
    public static Decision evaluate(Input in) {
        if (in == null) {
            // 无输入按从严处理：视为异常请求，不予发放
            return new Decision(true, true, List.of(RULE_ALREADY_FROZEN));
        }
        List<String> rules = new ArrayList<>();
        boolean freeze = false;

        if (in.accountFrozen()) {
            rules.add(RULE_ALREADY_FROZEN);
            freeze = true;
        }
        if (in.maxLinkedAccounts() > 0 && in.linkedAccountCount() > in.maxLinkedAccounts()) {
            rules.add(RULE_LINKED_ACCOUNT);
            freeze = true;
        }
        if (in.maxEarnInWindow() > 0 && in.earnCountInWindow() >= in.maxEarnInWindow()) {
            rules.add(RULE_FREQUENCY);
            freeze = true;
        }
        // 金额门槛：仅在配置了门槛且本次为交易场景（amountFen > 0）时判定；
        // amountFen <= 0 表示发布/评价等非交易来源，不走金额门槛。
        if (in.minAmountFen() > 0 && in.amountFen() > 0 && in.amountFen() < in.minAmountFen()) {
            rules.add(RULE_AMOUNT_THRESHOLD);
        }
        return rules.isEmpty() ? Decision.pass()
                : new Decision(true, freeze, List.copyOf(rules));
    }
}
