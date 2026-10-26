package com.idlefish.trade.im.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.im.assistant.ImIntent;
import com.idlefish.trade.im.assistant.KnowledgeMatcher;
import com.idlefish.trade.im.assistant.LlmClient;
import com.idlefish.trade.im.entity.Conversation;
import com.idlefish.trade.im.entity.ImAutoReply;
import com.idlefish.trade.im.entity.ImContentBlock;
import com.idlefish.trade.im.entity.ImDisputeWarning;
import com.idlefish.trade.im.entity.ImUnanswered;
import com.idlefish.trade.im.mapper.ImAutoReplyMapper;
import com.idlefish.trade.im.mapper.ImContentBlockMapper;
import com.idlefish.trade.im.mapper.ImDisputeWarningMapper;
import com.idlefish.trade.im.mapper.ImUnansweredMapper;
import com.idlefish.trade.im.mapper.MessageMapper;
import com.idlefish.trade.im.mapper.ConversationMapper;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.service.ItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * IM 智能助手编排服务单测（ITER-F06）：纯 Mockito 离线可跑。
 * <p>覆盖前置校验、内容拦截、裁决类/显式转人工、知识库自动作答、LLM 回落、
 * 低置信度入池、纠纷预警、反馈与转人工接管。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImAssistantServiceTest {

    @Mock
    ConversationMapper conversationMapper;
    @Mock
    MessageMapper messageMapper;
    @Mock
    ImAutoReplyMapper autoReplyMapper;
    @Mock
    ImDisputeWarningMapper warningMapper;
    @Mock
    ImContentBlockMapper blockMapper;
    @Mock
    ImUnansweredMapper unansweredMapper;
    @Mock
    ImKnowledgeService knowledgeService;
    @Mock
    LlmClient llmClient;
    @Mock
    ItemService itemService;
    @Mock
    MetricsRegistry metrics;

    private IdlefishProperties props;
    private ImAssistantService service;

    private static final String CONV = "c_1_2_100";

    @BeforeEach
    void setUp() {
        // 每用例重建配置，避免 enabled / 限流等状态在方法间共享（JUnit5 默认随机顺序）。
        props = new IdlefishProperties();
        service = new ImAssistantService(conversationMapper, messageMapper, autoReplyMapper, warningMapper,
                blockMapper, unansweredMapper, knowledgeService, llmClient, itemService, props, metrics);
    }

    private static Conversation conv(int takeover, int autoReply) {
        Conversation c = new Conversation();
        c.setId(1L);
        c.setConvId(CONV);
        c.setBuyerId(1L);
        c.setSellerId(2L);
        c.setItemId(100L);
        c.setAutoReplyEnabled(autoReply);
        c.setHumanTakeover(takeover);
        return c;
    }

    // ===== 前置校验 =====

    @Test
    void disabledReturnsEmpty() {
        props.getImAssistant().setEnabled(false);
        List<String> out = service.handleBuyerMessage(conv(0, 1), 1L, 2L, 100L, 5L, "什么时候发货");
        assertTrue(out.isEmpty());
        verifyNoInteractions(autoReplyMapper);
    }

    @Test
    void sellerClosedReturnsEmpty() {
        List<String> out = service.handleBuyerMessage(conv(0, 0), 1L, 2L, 100L, 5L, "什么时候发货");
        assertTrue(out.isEmpty());
        verifyNoInteractions(autoReplyMapper);
    }

    @Test
    void humanTakeoverReturnsEmpty() {
        List<String> out = service.handleBuyerMessage(conv(1, 1), 1L, 2L, 100L, 5L, "什么时候发货");
        assertTrue(out.isEmpty());
        verifyNoInteractions(autoReplyMapper);
    }

    @Test
    void convRateLimitReturnsEmpty() {
        props.getImAssistant().setMaxAutoReplyPerConvPerDay(1);
        when(autoReplyMapper.selectCount(any())).thenReturn(1L);
        List<String> out = service.handleBuyerMessage(conv(0, 1), 1L, 2L, 100L, 5L, "什么时候发货");
        assertTrue(out.isEmpty());
        verifyNoInteractions(knowledgeService);
    }

    // ===== 内容拦截（REQ-08） =====

    @Test
    void contactBlockEscalatesAndRecords() {
        Conversation c = conv(0, 1);
        List<String> out = service.handleBuyerMessage(c, 1L, 2L, 100L, 5L, "加我微信 abc12345 私下交易更便宜");
        assertEquals(1, out.size());
        assertTrue(out.get(0).contains("转接人工客服"));
        verify(blockMapper).insert(any(ImContentBlock.class));
        verify(conversationMapper).updateById(any(Conversation.class)); // 会话级接管
        ArgumentCaptor<ImAutoReply> captor = ArgumentCaptor.forClass(ImAutoReply.class);
        verify(autoReplyMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getEscalated());
        assertEquals("CONTENT_BLOCK", captor.getValue().getEscalateReason());
    }

    // ===== 升级（REQ-04/09/03） =====

    @Test
    void rulingEscalatesWithoutTakeover() {
        List<String> out = service.handleBuyerMessage(conv(0, 1), 1L, 2L, 100L, 5L, "这到底谁的责任，赔多少");
        assertEquals(1, out.size());
        ArgumentCaptor<ImAutoReply> captor = ArgumentCaptor.forClass(ImAutoReply.class);
        verify(autoReplyMapper).insert(captor.capture());
        assertEquals("RULING_QUESTION", captor.getValue().getEscalateReason());
        verify(conversationMapper, never()).updateById(any(Conversation.class));
    }

    @Test
    void explicitHumanMarksTakeover() {
        List<String> out = service.handleBuyerMessage(conv(0, 1), 1L, 2L, 100L, 5L, "我要转人工");
        assertEquals(1, out.size());
        verify(conversationMapper).updateById(any(Conversation.class));
        ArgumentCaptor<ImAutoReply> captor = ArgumentCaptor.forClass(ImAutoReply.class);
        verify(autoReplyMapper).insert(captor.capture());
        assertEquals("EXPLICIT_HUMAN", captor.getValue().getEscalateReason());
        verify(unansweredMapper, never()).insert(any(ImUnanswered.class));
    }

    @Test
    void lowConfidenceEscalatesAndPoolsQuestion() {
        List<String> out = service.handleBuyerMessage(conv(0, 1), 1L, 2L, 100L, 5L, "量子力学怎么入门呀");
        assertEquals(1, out.size());
        ArgumentCaptor<ImAutoReply> captor = ArgumentCaptor.forClass(ImAutoReply.class);
        verify(autoReplyMapper).insert(captor.capture());
        assertEquals("LOW_CONFIDENCE", captor.getValue().getEscalateReason());
        verify(unansweredMapper).insert(any(ImUnanswered.class));
        verify(conversationMapper, never()).updateById(any(Conversation.class));
    }

    // ===== 自动作答 =====

    @Test
    void knowledgeAutoReplyComposedWithIdentityAndSource() {
        when(knowledgeService.enabledCandidates()).thenReturn(List.of(new KnowledgeMatcher.Candidate(
                7L, 0L, ImIntent.SHIPPING, List.of("发货", "什么时候发"), "付款后48小时内发货", 0)));
        Item item = new Item();
        item.setCategoryId(null);
        when(itemService.view(100L)).thenReturn(item);
        List<String> out = service.handleBuyerMessage(conv(0, 1), 1L, 2L, 100L, 5L, "什么时候发货");
        assertEquals(1, out.size());
        assertEquals("【智能助手】付款后48小时内发货（来源：平台知识库）", out.get(0));
        ArgumentCaptor<ImAutoReply> captor = ArgumentCaptor.forClass(ImAutoReply.class);
        verify(autoReplyMapper).insert(captor.capture());
        assertEquals("KNOWLEDGE", captor.getValue().getSource());
        assertEquals(0, captor.getValue().getEscalated());
        assertEquals(0.75, captor.getValue().getConfidence(), 1e-9);
    }

    @Test
    void categoryEntryPreferredOverGeneral() {
        when(knowledgeService.enabledCandidates()).thenReturn(List.of(
                new KnowledgeMatcher.Candidate(1L, 0L, ImIntent.SHIPPING, List.of("什么时候发"), "通用答案", 0),
                new KnowledgeMatcher.Candidate(2L, 100L, ImIntent.SHIPPING, List.of("什么时候发"), "品类答案", 0)));
        Item item = new Item();
        item.setCategoryId(100L);
        when(itemService.view(100L)).thenReturn(item);
        List<String> out = service.handleBuyerMessage(conv(0, 1), 1L, 2L, 100L, 5L, "什么时候发");
        assertEquals("【智能助手】品类答案（来源：平台知识库）", out.get(0));
    }

    @Test
    void llmGeneratedWhenAvailable() {
        when(knowledgeService.enabledCandidates()).thenReturn(List.of(new KnowledgeMatcher.Candidate(
                7L, 0L, ImIntent.PRICE, List.of("便宜"), "议价请以页面价为准", 0)));
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn("可以关注平台优惠活动哦");
        List<String> out = service.handleBuyerMessage(conv(0, 1), 1L, 2L, 100L, 5L, "能便宜点吗");
        assertEquals(1, out.size());
        assertTrue(out.get(0).contains("可以关注平台优惠活动哦"));
        assertTrue(out.get(0).endsWith("（来源：智能助手生成）"));
    }

    /** REQ-12：LLM 不可用回落通用模板，不阻塞，且问题入池。 */
    @Test
    void fallbackTemplateWhenLlmUnavailable() {
        when(knowledgeService.enabledCandidates()).thenReturn(List.of(new KnowledgeMatcher.Candidate(
                7L, 0L, ImIntent.PRICE, List.of("便宜"), "议价请以页面价为准", 0)));
        when(llmClient.isAvailable()).thenReturn(false);
        List<String> out = service.handleBuyerMessage(conv(0, 1), 1L, 2L, 100L, 5L, "能便宜点吗");
        assertEquals(1, out.size());
        assertTrue(out.get(0).contains("转人工"));
        assertTrue(out.get(0).startsWith("【智能助手】"));
        verify(unansweredMapper).insert(any(ImUnanswered.class));
        ArgumentCaptor<ImAutoReply> captor = ArgumentCaptor.forClass(ImAutoReply.class);
        verify(autoReplyMapper).insert(captor.capture());
        assertEquals("TEMPLATE_FALLBACK", captor.getValue().getSource());
    }

    // ===== 纠纷预警（REQ-06） =====

    @Test
    void warnLevelRecordsWarningButKeepsAutoReply() {
        when(knowledgeService.enabledCandidates()).thenReturn(List.of(new KnowledgeMatcher.Candidate(
                7L, 0L, ImIntent.DISPUTE, List.of("骗子"), "已收到反馈，卖家会尽快核实处理", 0)));
        List<String> out = service.handleBuyerMessage(conv(0, 1), 1L, 2L, 100L, 5L,
                "你是骗子，我要315投诉，催了三次了");
        assertEquals(2, out.size()); // 预警卡 + 兜底应答
        assertTrue(out.get(0).contains("纠纷风险"));
        verify(warningMapper).insert(any(ImDisputeWarning.class));
        // WARN 级不接管：允许 persistRisk 累计风险分（updateById），但不得把会话置为人工接管
        verify(conversationMapper, never()).updateById(argThat((Conversation c) -> Integer.valueOf(1).equals(c.getHumanTakeover())));
    }

    @Test
    void highRiskEscalatesWithTakeover() {
        List<String> out = service.handleBuyerMessage(conv(0, 1), 1L, 2L, 100L, 5L,
                "骗子！假货！我要平台介入并起诉你");
        assertTrue(out.size() >= 2); // 预警卡 + 转人工提示
        verify(warningMapper).insert(any(ImDisputeWarning.class));
        verify(conversationMapper).updateById(any(Conversation.class)); // HIGH 接管
    }

    // ===== 用户端 =====

    @Test
    void requestHumanMarksTakeoverAndReturnsSummary() {
        when(conversationMapper.selectOne(any())).thenReturn(conv(0, 1));
        when(messageMapper.selectList(any())).thenReturn(List.of());
        ImAssistantService.EscalationResult r = service.requestHuman(1L, CONV);
        assertTrue(r.notice().contains("人工客服"));
        assertTrue(r.summary().contains("【会话摘要】"));
        verify(conversationMapper).updateById(any(Conversation.class));
        ArgumentCaptor<ImAutoReply> captor = ArgumentCaptor.forClass(ImAutoReply.class);
        verify(autoReplyMapper).insert(captor.capture());
        assertEquals("EXPLICIT_HUMAN", captor.getValue().getEscalateReason());
        assertEquals(1, captor.getValue().getEscalated());
    }

    @Test
    void requestHumanIdempotentWhenAlreadyTakenOver() {
        when(conversationMapper.selectOne(any())).thenReturn(conv(1, 1));
        ImAssistantService.EscalationResult r = service.requestHuman(1L, CONV);
        assertNull(r.notice());
        verify(conversationMapper, never()).updateById(any(Conversation.class));
    }

    @Test
    void autoReplyToggleOnlySeller() {
        when(conversationMapper.selectOne(any())).thenReturn(conv(0, 1));
        service.setAutoReplyEnabled(2L, CONV, false);
        verify(conversationMapper).updateById(any(Conversation.class));
        assertThrows(BizException.class, () -> service.setAutoReplyEnabled(1L, CONV, false));
    }

    @Test
    void statusReturnsFlags() {
        when(conversationMapper.selectOne(any())).thenReturn(conv(0, 1));
        Map<String, Object> data = service.status(1L, CONV);
        assertEquals(true, data.get("autoReplyEnabled"));
        assertEquals(false, data.get("humanTakeover"));
        assertEquals(true, data.get("assistantEnabled"));
    }

    @Test
    void feedbackUsefulRecorded() {
        ImAutoReply reply = new ImAutoReply();
        reply.setId(9L);
        reply.setBuyerId(1L);
        reply.setFeedback(ImAutoReply.FEEDBACK_NONE);
        reply.setConvId(CONV);
        when(autoReplyMapper.selectById(9L)).thenReturn(reply);
        service.feedback(1L, 9L, "USEFUL");
        ArgumentCaptor<ImAutoReply> captor = ArgumentCaptor.forClass(ImAutoReply.class);
        verify(autoReplyMapper).updateById(captor.capture());
        assertEquals("USEFUL", captor.getValue().getFeedback());
        verify(unansweredMapper, never()).insert(any(ImUnanswered.class));
    }

    @Test
    void feedbackUselessPoolsQuestion() {
        ImAutoReply reply = new ImAutoReply();
        reply.setId(9L);
        reply.setBuyerId(1L);
        reply.setFeedback(ImAutoReply.FEEDBACK_NONE);
        reply.setConvId(CONV);
        reply.setQuestion("能便宜点吗");
        reply.setIntent("PRICE");
        when(autoReplyMapper.selectById(9L)).thenReturn(reply);
        service.feedback(1L, 9L, "USELESS");
        verify(unansweredMapper).insert(any(ImUnanswered.class));
    }

    @Test
    void feedbackRejectedForNonBuyer() {
        ImAutoReply reply = new ImAutoReply();
        reply.setId(9L);
        reply.setBuyerId(1L);
        reply.setFeedback(ImAutoReply.FEEDBACK_NONE);
        when(autoReplyMapper.selectById(9L)).thenReturn(reply);
        BizException ex = assertThrows(BizException.class, () -> service.feedback(2L, 9L, "USEFUL"));
        assertEquals(Code.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void feedbackRejectedForUnknownReply() {
        assertThrows(BizException.class, () -> service.feedback(1L, 404L, "USEFUL"));
    }

    // ===== 统计 =====

    @Test
    void statsComputesRates() {
        when(autoReplyMapper.selectCount(isNull())).thenReturn(10L);
        com.idlefish.trade.im.vo.ImAssistantStatsVO stats = service.stats();
        assertEquals(10L, stats.getTotalReplies());
        assertEquals(1.0, stats.getAutoResolveRate(), 1e-9);
        assertEquals(0.0, stats.getEscalateRate(), 1e-9);
        assertEquals(0.0, stats.getSatisfactionRate(), 1e-9);
    }
}
