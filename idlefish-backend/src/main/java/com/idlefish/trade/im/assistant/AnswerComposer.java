package com.idlefish.trade.im.assistant;

/**
 * 应答组装纯函数（ITER-F06 / REQ-01 身份明示、REQ-05 金额分转元、REQ-10 来源标记、REQ-13 脱敏）。
 * <p>所有自动发出的回答统一带「【智能助手】」身份前缀与来源后缀（可审计），
 * 并在发出前经 {@link SensitiveInfoMasker} 脱敏；金额一律由「分」格式化为「元」展示。</p>
 */
public final class AnswerComposer {

    private AnswerComposer() {
    }

    /** 智能助手身份明示前缀（REQ-01：不得冒充卖家本人）。 */
    public static final String ASSISTANT_PREFIX = "【智能助手】";

    /** 回答来源（REQ-10 来源标记可审计）。 */
    public enum Source {
        /** 平台知识库话术。 */
        KNOWLEDGE("平台知识库"),
        /** 通用模板（LLM 不可用时的降级，REQ-12）。 */
        TEMPLATE_FALLBACK("平台通用模板"),
        /** 大模型生成（已脱敏，幻觉须绑知识库条目）。 */
        LLM("智能助手生成"),
        /** 转人工提示（升级通知，REQ-04/09）。 */
        ESCALATION("平台客服规则");

        private final String desc;

        Source(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    /**
     * 分 → 元格式化（两位小数）。例：12345 → "123.45"；100 → "1.00"；5 → "0.05"。
     */
    public static String formatFen(long fen) {
        boolean negative = fen < 0;
        long abs = Math.abs(fen);
        long yuan = abs / 100;
        long cent = abs % 100;
        String body = yuan + "." + (cent < 10 ? "0" + cent : String.valueOf(cent));
        return negative ? "-" + body : body;
    }

    /**
     * 组装最终对买家可见的回答文本：身份前缀 + 脱敏正文 + 来源后缀。
     */
    public static String compose(String answer, Source source) {
        String body = answer == null ? "" : answer.trim();
        body = SensitiveInfoMasker.mask(body);
        if (body.isEmpty()) {
            body = "我暂时无法回答这个问题，已为你转接人工客服。";
        }
        return ASSISTANT_PREFIX + body + "（来源：" + source.getDesc() + "）";
    }
}
