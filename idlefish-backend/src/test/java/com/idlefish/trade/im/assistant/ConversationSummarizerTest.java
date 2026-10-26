package com.idlefish.trade.im.assistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话摘要纯函数单测（ITER-F06 / REQ-07）。
 */
class ConversationSummarizerTest {

    @Test
    void summarizesRequestsAnswersAndEvidence() {
        List<ConversationSummarizer.Msg> msgs = List.of(
                new ConversationSummarizer.Msg(1L, false, "什么时候发货", "text", 1L, "2026-10-25 10:00:00"),
                new ConversationSummarizer.Msg(2L, true, "【智能助手】付款后48小时内发货（来源：平台知识库）", "text", 2L, "2026-10-25 10:00:05"),
                new ConversationSummarizer.Msg(1L, false, "", "image", 3L, "2026-10-25 10:01:00"),
                new ConversationSummarizer.Msg(1L, false, "能便宜点吗", "text", 4L, "2026-10-25 10:02:00"));
        ConversationSummarizer.Summary s = ConversationSummarizer.summarize(100L, 1L, msgs, 3);
        assertEquals(4, s.messageCount());
        assertEquals(3, s.buyerCount());
        assertEquals(0, s.sellerCount());
        assertEquals(1, s.assistantCount());
        assertEquals(2, s.buyerRequests().size());
        assertEquals(1, s.autoAnswered().size());
        assertEquals(List.of("消息#3[图片]"), s.evidenceRefs());
        assertEquals("2026-10-25 10:02:00", s.lastActiveAt());
        String text = s.toText();
        assertTrue(text.contains("【会话摘要】商品ID=100"));
        assertTrue(text.contains("买家诉求：什么时候发货；能便宜点吗"));
        assertTrue(text.contains("已自动答复：【智能助手】付款后48小时内发货（来源：平台知识库）"));
        assertTrue(text.contains("证据引用：消息#3[图片]"));
    }

    @Test
    void sellerMessagesCountedSeparately() {
        List<ConversationSummarizer.Msg> msgs = List.of(
                new ConversationSummarizer.Msg(2L, false, "在的", "text", 1L, "2026-10-25 10:00:00"),
                new ConversationSummarizer.Msg(1L, false, "你好", "text", 2L, "2026-10-25 10:00:01"));
        ConversationSummarizer.Summary s = ConversationSummarizer.summarize(null, 1L, msgs, 3);
        assertEquals(1, s.buyerCount());
        assertEquals(1, s.sellerCount());
        assertEquals("你好", s.buyerRequests().get(0));
    }

    @Test
    void emptyMessagesSafe() {
        ConversationSummarizer.Summary s = ConversationSummarizer.summarize(1L, 1L, List.of(), 3);
        assertEquals(0, s.messageCount());
        assertTrue(s.buyerRequests().isEmpty());
        assertTrue(s.toText().contains("（无）"));
    }

    @Test
    void longSnippetTruncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("很长的问题描述");
        }
        List<ConversationSummarizer.Msg> msgs = List.of(
                new ConversationSummarizer.Msg(1L, false, sb.toString(), "text", 1L, null));
        ConversationSummarizer.Summary s = ConversationSummarizer.summarize(1L, 1L, msgs, 3);
        assertEquals(41, s.buyerRequests().get(0).length()); // 40 + 省略号
    }
}
