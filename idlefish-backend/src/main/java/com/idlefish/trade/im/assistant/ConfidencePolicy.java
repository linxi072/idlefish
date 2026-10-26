package com.idlefish.trade.im.assistant;

/**
 * 置信度分流策略纯函数（ITER-F06 / REQ-03 低置信度转人工、REQ-12 LLM 不可用回落模板）。
 * <ul>
 *   <li>confidence &ge; threshold：模板化自动作答（知识库命中）；</li>
 *   <li>threshold/2 &le; confidence &lt; threshold：LLM 可用则试探生成，不可用回落通用模板（不阻塞）；</li>
 *   <li>confidence &lt; threshold/2：转人工，并记入未命中问题池（REQ-14）。</li>
 * </ul>
 */
public final class ConfidencePolicy {

    private ConfidencePolicy() {
    }

    /** 分流决策。 */
    public enum Decision {
        /** 知识库模板直接作答。 */
        AUTO_REPLY,
        /** 置信度中等，尝试 LLM 生成（调用方需处理失败回落）。 */
        LLM_TRY,
        /** LLM 不可用时的通用模板回落（不阻塞买家，REQ-12）。 */
        FALLBACK_TEMPLATE,
        /** 低置信度转人工。 */
        HUMAN
    }

    public static Decision decide(double confidence, double threshold, boolean llmAvailable) {
        if (confidence >= threshold) {
            return Decision.AUTO_REPLY;
        }
        if (confidence >= threshold * 0.5) {
            return llmAvailable ? Decision.LLM_TRY : Decision.FALLBACK_TEMPLATE;
        }
        return Decision.HUMAN;
    }
}
