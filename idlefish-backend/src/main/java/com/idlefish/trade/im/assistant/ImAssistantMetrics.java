package com.idlefish.trade.im.assistant;

/**
 * IM 智能助手埋点指标名（ITER-F06 / PRD-P2-F06 §七）。
 * <p>对应 PRD 事件：im_intent_detect / im_auto_reply_send / im_auto_reply_feedback /
 * im_escalate_human / im_dispute_warning / im_content_block / im_fallback_template。
 * 命名沿用全仓扁平点号风格（如 {@code pay.notify.v3.success}），由 {@code MetricsRegistry} 计数。</p>
 */
public final class ImAssistantMetrics {

    private ImAssistantMetrics() {
    }

    /** 意图识别（按意图追加后缀，如 im.assistant.intent_detect.SHIPPING）。 */
    public static final String INTENT_DETECT = "im.assistant.intent_detect";
    /** 自动应答发送（按来源追加后缀，如 .KNOWLEDGE / .TEMPLATE_FALLBACK / .LLM）。 */
    public static final String AUTO_REPLY_SEND = "im.assistant.auto_reply_send";
    /** 自动应答反馈（.useful / .useless）。 */
    public static final String AUTO_REPLY_FEEDBACK = "im.assistant.auto_reply_feedback";
    /** 转人工（按原因追加后缀，如 .RULING_QUESTION）。 */
    public static final String ESCALATE_HUMAN = "im.assistant.escalate_human";
    /** 纠纷预警（.warn / .high）。 */
    public static final String DISPUTE_WARNING = "im.assistant.dispute_warning";
    /** 内容拦截（引流/敏感词，留痕风控）。 */
    public static final String CONTENT_BLOCK = "im.assistant.content_block";
    /** 模板降级（LLM 不可用回落，REQ-12）。 */
    public static final String FALLBACK_TEMPLATE = "im.assistant.fallback_template";
    /** 未命中问题入池（REQ-14）。 */
    public static final String UNANSWERED_POOL = "im.assistant.unanswered_pool";
    /** 会话级刷量限流命中（边界场景 8）。 */
    public static final String CONV_RATE_LIMIT = "im.assistant.conv_rate_limit";
}
