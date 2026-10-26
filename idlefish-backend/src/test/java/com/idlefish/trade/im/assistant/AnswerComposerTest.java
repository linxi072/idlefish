package com.idlefish.trade.im.assistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 应答组装纯函数单测（ITER-F06 / REQ-01/05/10/13）。
 */
class AnswerComposerTest {

    @Test
    void formatFenToYuan() {
        assertEquals("123.45", AnswerComposer.formatFen(12345));
        assertEquals("1.00", AnswerComposer.formatFen(100));
        assertEquals("0.05", AnswerComposer.formatFen(5));
        assertEquals("0.00", AnswerComposer.formatFen(0));
        assertEquals("-2.50", AnswerComposer.formatFen(-250));
    }

    @Test
    void composeAddsIdentityAndSource() {
        String out = AnswerComposer.compose("付款后48小时内发货", AnswerComposer.Source.KNOWLEDGE);
        assertTrue(out.startsWith("【智能助手】"));
        assertTrue(out.endsWith("（来源：平台知识库）"));
        assertTrue(out.contains("付款后48小时内发货"));
    }

    /** REQ-13：组装前脱敏，敏感信息不回显。 */
    @Test
    void composeMasksSensitive() {
        String out = AnswerComposer.compose("请联系13812348000", AnswerComposer.Source.KNOWLEDGE);
        assertTrue(out.contains("138****8000"));
        assertTrue(!out.contains("13812348000"));
    }

    @Test
    void composeBlankAnswerFallsBackToHumanNotice() {
        String out = AnswerComposer.compose("  ", AnswerComposer.Source.TEMPLATE_FALLBACK);
        assertTrue(out.contains("转接人工客服"));
        assertTrue(out.startsWith("【智能助手】"));
    }

    @Test
    void composeEscalationSource() {
        String out = AnswerComposer.compose("已为你转接人工客服。", AnswerComposer.Source.ESCALATION);
        assertTrue(out.endsWith("（来源：平台客服规则）"));
    }
}
