package com.idlefish.trade.im.assistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 转人工判定纯函数单测（ITER-F06 / REQ-04/06/08/09）。
 */
class EscalationPolicyTest {

    private static EscalationPolicy.Input input(ImIntent intent, boolean explicitHuman, boolean abuse,
                                                double confidence, DisputeSignalDetector.Level level) {
        return new EscalationPolicy.Input(intent, explicitHuman, abuse, confidence, 0.6, level, false);
    }

    @Test
    void wantsHumanDetectsKeywords() {
        assertTrue(EscalationPolicy.wantsHuman("我要转人工"));
        assertTrue(EscalationPolicy.wantsHuman("找人工客服"));
        assertTrue(EscalationPolicy.wantsHuman("有人吗真人"));
        assertFalse(EscalationPolicy.wantsHuman("你好"));
    }

    /** REQ-04：裁决类 → 当条转交（takeover=false），本会话助手对后续问题仍可应答。 */
    @Test
    void rulingEscalatesWithoutTakeover() {
        EscalationPolicy.Decision d = EscalationPolicy.decide(input(ImIntent.RULING, false, false, 1.0, DisputeSignalDetector.Level.NONE));
        assertTrue(d.escalate());
        assertEquals(EscalationPolicy.Reason.RULING_QUESTION, d.reason());
        assertFalse(d.takeover());
    }

    /** REQ-09：显式转人工 → 会话级接管。 */
    @Test
    void explicitHumanTakesOver() {
        EscalationPolicy.Decision d = EscalationPolicy.decide(input(ImIntent.CHITCHAT, true, false, 1.0, DisputeSignalDetector.Level.NONE));
        assertTrue(d.escalate());
        assertEquals(EscalationPolicy.Reason.EXPLICIT_HUMAN, d.reason());
        assertTrue(d.takeover());
    }

    @Test
    void abuseTakesOver() {
        EscalationPolicy.Decision d = EscalationPolicy.decide(input(ImIntent.DISPUTE, false, true, 1.0, DisputeSignalDetector.Level.NONE));
        assertEquals(EscalationPolicy.Reason.ABUSE, d.reason());
        assertTrue(d.takeover());
    }

    /** REQ-06：仅 HIGH 级纠纷风险接管；WARN 仅预警不接管。 */
    @Test
    void highRiskTakesOverWarnDoesNot() {
        assertTrue(EscalationPolicy.decide(input(ImIntent.DISPUTE, false, false, 1.0, DisputeSignalDetector.Level.HIGH)).takeover());
        assertFalse(EscalationPolicy.decide(input(ImIntent.DISPUTE, false, false, 1.0, DisputeSignalDetector.Level.WARN)).escalate());
    }

    /** REQ-03：低置信度 → 当条转交。 */
    @Test
    void lowConfidenceEscalatesWithoutTakeover() {
        EscalationPolicy.Decision d = EscalationPolicy.decide(input(ImIntent.UNKNOWN, false, false, 0.1, DisputeSignalDetector.Level.NONE));
        assertTrue(d.escalate());
        assertEquals(EscalationPolicy.Reason.LOW_CONFIDENCE, d.reason());
        assertFalse(d.takeover());
    }

    @Test
    void normalFlowNoEscalation() {
        EscalationPolicy.Decision d = EscalationPolicy.decide(input(ImIntent.SHIPPING, false, false, 0.8, DisputeSignalDetector.Level.NONE));
        assertFalse(d.escalate());
    }

    @Test
    void alreadyHumanConvNeverEscalates() {
        EscalationPolicy.Input in = new EscalationPolicy.Input(ImIntent.RULING, true, true, 0.0, 0.6, DisputeSignalDetector.Level.HIGH, true);
        assertFalse(EscalationPolicy.decide(in).escalate());
    }
}
