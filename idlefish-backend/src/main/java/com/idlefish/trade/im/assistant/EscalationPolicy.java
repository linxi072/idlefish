package com.idlefish.trade.im.assistant;

import java.util.List;

/**
 * 转人工判定纯函数（ITER-F06 / REQ-04 裁决类禁自动作答、REQ-09 用户要求转人工即停、
 * REQ-06 纠纷风险升级、REQ-08 引流拦截转人工）。
 * <p>判定优先级：引流拦截 &gt; 裁决类 &gt; 显式转人工 &gt; 辱骂 &gt; 高风险纠纷 &gt; 低置信度。</p>
 * <p>{@link Decision#takeover()} 区分两类升级：
 * <b>会话级接管</b>（置位后本会话停止一切自动作答，REQ-09）与
 * <b>当条转交</b>（仅本条不自动作答，助手对后续清晰问题仍可应答，兼顾 REQ-03 与自动解决率）。</p>
 */
public final class EscalationPolicy {

    private EscalationPolicy() {
    }

    /** 升级原因。 */
    public enum Reason {
        /** 站外联系方式 / 引流话术拦截（REQ-08）。 */
        CONTENT_BLOCK,
        /** 裁决类诉求（退款/责任/赔付判定，REQ-04）。 */
        RULING_QUESTION,
        /** 用户显式要求转人工（REQ-09）。 */
        EXPLICIT_HUMAN,
        /** 辱骂攻击。 */
        ABUSE,
        /** 纠纷风险累计达高级别（REQ-06）。 */
        DISPUTE_RISK,
        /** 置信度过低（REQ-03）。 */
        LOW_CONFIDENCE
    }

    /** 显式转人工关键词（REQ-09）。 */
    private static final List<String> HUMAN_WORDS = List.of(
            "转人工", "人工客服", "找客服", "真人", "人工服务", "客服接", "人工处理", "真人客服", "找平台客服");

    /** 判定输入。 */
    public record Input(ImIntent intent, boolean explicitHuman, boolean abuse, double confidence,
                        double threshold, DisputeSignalDetector.Level riskLevel, boolean convAlreadyHuman) {
    }

    /** 判定结果。 */
    public record Decision(boolean escalate, Reason reason, String notice, boolean takeover) {

        /** 不升级哨兵。 */
        public static final Decision NONE = new Decision(false, null, null, false);
    }

    /** 用户是否显式要求转人工（REQ-09：即停自动作答）。 */
    public static boolean wantsHuman(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.toLowerCase();
        for (String w : HUMAN_WORDS) {
            if (t.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 综合判定是否升级。会话已处于人工接管状态时不再重复升级（返回 {@link Decision#NONE}）。
     */
    public static Decision decide(Input in) {
        if (in == null || in.convAlreadyHuman()) {
            return Decision.NONE;
        }
        if (in.abuse()) {
            return new Decision(true, Reason.ABUSE,
                    "已记录本次沟通情况并转接人工客服跟进。", true);
        }
        if (in.intent() == ImIntent.RULING) {
            return new Decision(true, Reason.RULING_QUESTION,
                    "该问题涉及退款/责任/赔付判定，按平台规定须由人工客服处理，已为你转接，请稍候。", false);
        }
        if (in.explicitHuman()) {
            return new Decision(true, Reason.EXPLICIT_HUMAN,
                    "已为你转接人工客服，本会话将由人工跟进，请稍候。", true);
        }
        if (in.riskLevel() == DisputeSignalDetector.Level.HIGH) {
            return new Decision(true, Reason.DISPUTE_RISK,
                    "系统检测到本会话纠纷风险较高，已转接人工客服处理。", true);
        }
        if (in.confidence() < in.threshold() * 0.5) {
            return new Decision(true, Reason.LOW_CONFIDENCE,
                    "这个问题我暂时无法确定，已记录并转交人工客服回复。", false);
        }
        return Decision.NONE;
    }
}
