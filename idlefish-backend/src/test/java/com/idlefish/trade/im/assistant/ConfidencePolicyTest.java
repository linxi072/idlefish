package com.idlefish.trade.im.assistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 置信度分流纯函数单测（ITER-F06 / REQ-03/12）。
 */
class ConfidencePolicyTest {

    @Test
    void highConfidenceAutoReplies() {
        assertEquals(ConfidencePolicy.Decision.AUTO_REPLY,
                ConfidencePolicy.decide(0.75, 0.6, true));
        assertEquals(ConfidencePolicy.Decision.AUTO_REPLY,
                ConfidencePolicy.decide(0.6, 0.6, false));
    }

    @Test
    void mediumConfidenceTriesLlmWhenAvailable() {
        assertEquals(ConfidencePolicy.Decision.LLM_TRY,
                ConfidencePolicy.decide(0.45, 0.6, true));
    }

    /** REQ-12：LLM 不可用回落模板，不阻塞。 */
    @Test
    void mediumConfidenceFallsBackWhenLlmUnavailable() {
        assertEquals(ConfidencePolicy.Decision.FALLBACK_TEMPLATE,
                ConfidencePolicy.decide(0.45, 0.6, false));
    }

    @Test
    void lowConfidenceGoesHuman() {
        assertEquals(ConfidencePolicy.Decision.HUMAN,
                ConfidencePolicy.decide(0.29, 0.6, true));
        assertEquals(ConfidencePolicy.Decision.HUMAN,
                ConfidencePolicy.decide(0.0, 0.6, false));
    }
}
