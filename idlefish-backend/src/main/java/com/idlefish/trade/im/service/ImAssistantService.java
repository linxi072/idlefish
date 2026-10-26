package com.idlefish.trade.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.common.util.SensitiveWords;
import com.idlefish.trade.im.assistant.AnswerComposer;
import com.idlefish.trade.im.assistant.ConfidencePolicy;
import com.idlefish.trade.im.assistant.ConversationSummarizer;
import com.idlefish.trade.im.assistant.DisputeSignalDetector;
import com.idlefish.trade.im.assistant.DisputeSignalDetector.Level;
import com.idlefish.trade.im.assistant.DisputeSignalDetector.Signal;
import com.idlefish.trade.im.assistant.EscalationPolicy;
import com.idlefish.trade.im.assistant.ImAssistantMetrics;
import com.idlefish.trade.im.assistant.ImIntent;
import com.idlefish.trade.im.assistant.IntentDetector;
import com.idlefish.trade.im.assistant.KnowledgeMatcher;
import com.idlefish.trade.im.assistant.LlmClient;
import com.idlefish.trade.im.assistant.SensitiveInfoMasker;
import com.idlefish.trade.im.entity.Conversation;
import com.idlefish.trade.im.entity.ImAutoReply;
import com.idlefish.trade.im.entity.ImContentBlock;
import com.idlefish.trade.im.entity.ImDisputeWarning;
import com.idlefish.trade.im.entity.ImUnanswered;
import com.idlefish.trade.im.entity.Message;
import com.idlefish.trade.im.mapper.ImAutoReplyMapper;
import com.idlefish.trade.im.mapper.ImContentBlockMapper;
import com.idlefish.trade.im.mapper.ImDisputeWarningMapper;
import com.idlefish.trade.im.mapper.ImUnansweredMapper;
import com.idlefish.trade.im.vo.ImAssistantStatsVO;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.service.ItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IM 智能助手编排服务（ITER-F06 / PRD-P2-F06）。
 *
 * <p><b>主链路</b>（买家消息后由 {@link ImService#send} best-effort 触发，绝不阻塞消息主流程）：
 * <ol>
 *   <li>总开关 / 卖家会话级开关 / 人工接管标记 / 会话级刷量限流 前置校验；</li>
 *   <li>内容安全：站外联系方式与引流话术拦截并记风控，直接转人工（REQ-08）；</li>
 *   <li>意图识别（裁决类命中即转人工，REQ-04）；</li>
 *   <li>纠纷信号检测与风险分累计，达阈值下发预警卡并提示一键发起 F-17 维权（REQ-06）；</li>
 *   <li>知识库匹配（品类话术优先 + 通用回落，REQ-11）与置信度分流（REQ-03）；</li>
 *   <li>LLM 试探生成（幻觉受知识库上下文约束），不可用回落通用模板（REQ-12）；</li>
 *   <li>组装（身份明示 REQ-01 / 金额分转元 REQ-05 / 来源标记 REQ-10 / 脱敏 REQ-13）并留痕。</li>
 * </ol>
 *
 * <p><b>升级语义</b>：{@link EscalationPolicy.Decision#takeover()}=true 的升级（显式转人工 /
 * 辱骂 / 引流拦截 / 高风险纠纷）置位会话人工接管标记，本会话停止一切自动作答（REQ-09）；
 * 裁决类与低置信度为当条转交，助手对后续清晰问题仍可应答（兼顾自动解决率指标）。</p>
 */
@Service
public class ImAssistantService {

    private static final Logger log = LoggerFactory.getLogger(ImAssistantService.class);

    /** 摘要保留的诉求/答复条数。 */
    private static final int MAX_SNIPPETS = 3;
    /** 会话摘要加载的最近消息条数。 */
    private static final int SUMMARY_MSG_LIMIT = 50;
    /** 通用回落模板（LLM 不可用 / 未命中时的兜底，REQ-12 不阻塞）。 */
    private static final String GENERIC_FALLBACK =
            "你的问题我已记录，卖家上线后会尽快回复；如需人工客服，可在会话页点击「转人工」。";

    private final com.idlefish.trade.im.mapper.ConversationMapper conversationMapper;
    private final com.idlefish.trade.im.mapper.MessageMapper messageMapper;
    private final ImAutoReplyMapper autoReplyMapper;
    private final ImDisputeWarningMapper warningMapper;
    private final ImContentBlockMapper blockMapper;
    private final ImUnansweredMapper unansweredMapper;
    private final ImKnowledgeService knowledgeService;
    private final LlmClient llmClient;
    private final ItemService itemService;
    private final IdlefishProperties props;
    private final MetricsRegistry metrics;

    public ImAssistantService(com.idlefish.trade.im.mapper.ConversationMapper conversationMapper,
                              com.idlefish.trade.im.mapper.MessageMapper messageMapper,
                              ImAutoReplyMapper autoReplyMapper,
                              ImDisputeWarningMapper warningMapper,
                              ImContentBlockMapper blockMapper,
                              ImUnansweredMapper unansweredMapper,
                              ImKnowledgeService knowledgeService,
                              LlmClient llmClient,
                              ItemService itemService,
                              IdlefishProperties props,
                              MetricsRegistry metrics) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.autoReplyMapper = autoReplyMapper;
        this.warningMapper = warningMapper;
        this.blockMapper = blockMapper;
        this.unansweredMapper = unansweredMapper;
        this.knowledgeService = knowledgeService;
        this.llmClient = llmClient;
        this.itemService = itemService;
        this.props = props;
        this.metrics = metrics;
    }

    // ==================== 主链路 ====================

    /**
     * 买家消息后的助手处理（best-effort）。
     *
     * @return 需要以卖家侧身份发出的助手消息（0~N 条：预警卡 / 应答 / 转人工提示）；
     * 空列表表示不发言。任何异常都被吞掉并记录，绝不影响买家消息主流程。
     */
    public List<String> handleBuyerMessage(Conversation conv, Long buyerId, Long sellerId, Long itemId,
                                           Long triggerMessageId, String text) {
        if (conv == null) {
            return List.of();
        }
        String convId = conv.getConvId();
        try {
            IdlefishProperties.ImAssistant cfg = props.getImAssistant();
            if (!cfg.isEnabled()) {
                return List.of();
            }
            if (Integer.valueOf(0).equals(conv.getAutoReplyEnabled())) {
                return List.of(); // 卖家已关闭本会话代答
            }
            if (Integer.valueOf(1).equals(conv.getHumanTakeover())) {
                return List.of(); // 人工接管中，停止一切自动作答（REQ-09）
            }
            if (rateLimited(convId, cfg.getMaxAutoReplyPerConvPerDay())) {
                metrics.increment(ImAssistantMetrics.CONV_RATE_LIMIT);
                return List.of();
            }

            // 1) 内容安全：站外联系方式 / 引流话术拦截并记风控（REQ-08）
            if (SensitiveWords.hit(text)) {
                boolean contact = SensitiveInfoMasker.hasSensitive(text);
                recordBlock(convId, buyerId, contact ? ImContentBlock.TYPE_CONTACT : ImContentBlock.TYPE_LEAD, text);
                metrics.increment(ImAssistantMetrics.CONTENT_BLOCK);
                return escalate(conv, buyerId, sellerId, itemId, triggerMessageId, text,
                        new EscalationPolicy.Decision(true, EscalationPolicy.Reason.CONTENT_BLOCK,
                                "为保障交易安全，请勿在沟通中留下站外联系方式或进行站外交易，本会话已转接人工客服。", true));
            }

            // 2) 意图识别（埋点 im_intent_detect）
            ImIntent intent = IntentDetector.detect(text);
            metrics.increment(ImAssistantMetrics.INTENT_DETECT + "." + intent.name());

            // 3) 纠纷信号与风险分累计（REQ-06）
            List<Signal> signals = new ArrayList<>(DisputeSignalDetector.detect(text));
            if (slowReply(conv, cfg.getSellerSlowReplyHours())) {
                signals.add(Signal.SLOW_REPLY);
            }
            int base = freshRisk(conv, cfg.getDisputeSignalWindowHours()) ? nz(conv.getDisputeRiskScore()) : 0;
            int riskScore = Math.min(100, base + DisputeSignalDetector.riskScore(signals));
            Level riskLevel = DisputeSignalDetector.level(riskScore, cfg.getDisputeWarnThreshold());
            List<String> out = new ArrayList<>();
            if (!signals.isEmpty()) {
                persistRisk(conv, riskScore);
            }
            if (riskLevel != Level.NONE && !signals.isEmpty()) {
                String warningNotice = recordWarning(conv, buyerId, sellerId, riskScore, riskLevel, signals);
                metrics.increment(ImAssistantMetrics.DISPUTE_WARNING + "." + riskLevel.name().toLowerCase(Locale.ROOT));
                out.add(warningNotice);
            }

            // 4) 知识库匹配（品类优先 REQ-11）与置信度分流（REQ-03）
            Long categoryId = resolveCategory(itemId);
            KnowledgeMatcher.MatchResult match = KnowledgeMatcher.match(
                    knowledgeService.enabledCandidates(), intent, categoryId, text);
            double confidence = match.confidence();
            ConfidencePolicy.Decision flow = ConfidencePolicy.decide(
                    confidence, cfg.getConfidenceThreshold(), llmClient.isAvailable());

            // 5) 升级判定（REQ-04 裁决类 / REQ-09 显式转人工 / REQ-06 高风险 / REQ-03 低置信度）
            EscalationPolicy.Decision esc = EscalationPolicy.decide(new EscalationPolicy.Input(
                    intent, EscalationPolicy.wantsHuman(text), signals.contains(Signal.ABUSE),
                    confidence, cfg.getConfidenceThreshold(), riskLevel, false));
            if (esc.escalate()) {
                out.addAll(escalate(conv, buyerId, sellerId, itemId, triggerMessageId, text, esc));
                return out;
            }

            // 6) 组装应答（REQ-01/05/10/12/13）
            String answer;
            AnswerComposer.Source source;
            if (flow == ConfidencePolicy.Decision.AUTO_REPLY) {
                answer = match.candidate().answerTemplate();
                source = AnswerComposer.Source.KNOWLEDGE;
            } else if (flow == ConfidencePolicy.Decision.LLM_TRY) {
                String generated = llmClient.chat(cfg.getLlm().getSystemPrompt(),
                        buildLlmUserMessage(text, categoryId, match));
                if (generated != null && !generated.isBlank()) {
                    answer = generated;
                    source = AnswerComposer.Source.LLM;
                } else {
                    metrics.increment(ImAssistantMetrics.FALLBACK_TEMPLATE);
                    answer = GENERIC_FALLBACK;
                    source = AnswerComposer.Source.TEMPLATE_FALLBACK;
                    addUnanswered(text, intent, categoryId, convId);
                }
            } else {
                // FALLBACK_TEMPLATE：LLM 不可用，回落通用模板不阻塞（REQ-12）
                metrics.increment(ImAssistantMetrics.FALLBACK_TEMPLATE);
                answer = GENERIC_FALLBACK;
                source = AnswerComposer.Source.TEMPLATE_FALLBACK;
                addUnanswered(text, intent, categoryId, convId);
            }
            String reply = AnswerComposer.compose(answer, source);
            recordReply(convId, itemId, buyerId, sellerId, triggerMessageId, intent, source.name(),
                    confidence, text, reply, false, null, null);
            metrics.increment(ImAssistantMetrics.AUTO_REPLY_SEND + "." + source.name());
            out.add(reply);
            return out;
        } catch (Exception e) {
            log.warn("[im-assistant] 处理异常（best-effort，不阻塞消息主流程） convId={}", convId, e);
            return List.of();
        }
    }

    // ==================== 用户端 ====================

    /**
     * 买家 / 会话参与方主动转人工（REQ-09）：置位人工接管并返回待发送的提示与摘要。
     * 已在人工接管中的会话返回空提示（notice=null），由控制器跳过发送。
     */
    public EscalationResult requestHuman(Long userId, String convId) {
        Conversation conv = loadConv(convId);
        requireParticipant(conv, userId);
        if (Integer.valueOf(1).equals(conv.getHumanTakeover())) {
            return new EscalationResult(convId, peerOf(conv, userId), userId, conv.getItemId(), null, null, null);
        }
        EscalationPolicy.Decision decision = EscalationPolicy.decide(new EscalationPolicy.Input(
                ImIntent.UNKNOWN, true, false, 1.0, props.getImAssistant().getConfidenceThreshold(),
                Level.NONE, false));
        markTakeover(conv);
        metrics.increment(ImAssistantMetrics.ESCALATE_HUMAN + "." + decision.reason().name());
        String summary = buildSummary(conv, conv.getBuyerId());
        String reply = AnswerComposer.compose(decision.notice(), AnswerComposer.Source.ESCALATION);
        Long replyId = recordReply(convId, conv.getItemId(), conv.getBuyerId(), conv.getSellerId(), null,
                ImIntent.UNKNOWN, ImAutoReply.SOURCE_ESCALATION, 0.0, null, reply, true,
                decision.reason().name(), summary);
        log.info("[im-assistant] 转人工接管 convId={} buyerId={} summary={}", convId, conv.getBuyerId(), summary);
        return new EscalationResult(convId, peerOf(conv, userId), userId, conv.getItemId(), reply, replyId, summary);
    }

    /**
     * 卖家会话级自动代答开关（仅会话卖家可操作；买家调用返回无权限）。
     */
    public void setAutoReplyEnabled(Long userId, String convId, boolean enabled) {
        Conversation conv = loadConv(convId);
        if (!userId.equals(conv.getSellerId())) {
            throw new BizException(Code.FORBIDDEN, "仅卖家可开关本会话的自动代答");
        }
        if (Integer.valueOf(1).equals(conv.getHumanTakeover())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "人工接管中，如需恢复自动代答请先解除接管");
        }
        Conversation upd = new Conversation();
        upd.setId(conv.getId());
        upd.setAutoReplyEnabled(enabled ? 1 : 0);
        conversationMapper.updateById(upd);
    }

    /** 解除人工接管（卖家/客服恢复自动代答）。 */
    public void clearTakeover(Long userId, String convId) {
        Conversation conv = loadConv(convId);
        if (!userId.equals(conv.getSellerId())) {
            throw new BizException(Code.FORBIDDEN, "仅卖家可解除人工接管");
        }
        Conversation upd = new Conversation();
        upd.setId(conv.getId());
        upd.setHumanTakeover(0);
        conversationMapper.updateById(upd);
    }

    /** 会话助手状态（供前端展示「转人工」按钮与代答提示）。 */
    public Map<String, Object> status(Long userId, String convId) {
        Conversation conv = loadConv(convId);
        requireParticipant(conv, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("convId", convId);
        data.put("autoReplyEnabled", Integer.valueOf(1).equals(conv.getAutoReplyEnabled()));
        data.put("humanTakeover", Integer.valueOf(1).equals(conv.getHumanTakeover()));
        data.put("assistantEnabled", props.getImAssistant().isEnabled());
        return data;
    }

    /**
     * 自动应答反馈（REQ-14：点踩「没用」入未命中问题池）。仅提问买家可评价，重复评价幂等忽略。
     */
    public void feedback(Long userId, Long replyId, String feedback) {
        ImAutoReply reply = autoReplyMapper.selectById(replyId);
        if (reply == null) {
            throw new BizException(Code.IM_AUTO_REPLY_NOT_FOUND);
        }
        if (!userId.equals(reply.getBuyerId())) {
            throw new BizException(Code.FORBIDDEN, "仅提问买家可评价该回复");
        }
        if (!ImAutoReply.FEEDBACK_USEFUL.equals(feedback) && !ImAutoReply.FEEDBACK_USELESS.equals(feedback)) {
            throw new BizException(Code.PARAM_INVALID, "反馈仅支持 USEFUL/USELESS");
        }
        if (!ImAutoReply.FEEDBACK_NONE.equals(reply.getFeedback())) {
            return; // 已评价：幂等忽略
        }
        ImAutoReply upd = new ImAutoReply();
        upd.setId(replyId);
        upd.setFeedback(feedback);
        autoReplyMapper.updateById(upd);
        metrics.increment(ImAssistantMetrics.AUTO_REPLY_FEEDBACK + "." + feedback.toLowerCase(Locale.ROOT));
        if (ImAutoReply.FEEDBACK_USELESS.equals(feedback)) {
            addUnanswered(reply.getQuestion(), parseIntentOrNull(reply.getIntent()), null, reply.getConvId());
        }
    }

    // ==================== 运营后台 ====================

    /** 未命中问题池分页（可按状态过滤，按热度降序）。 */
    public IPage<ImUnanswered> unansweredPage(String status, int page, int size) {
        return unansweredMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<ImUnanswered>()
                .eq(status != null && !status.isBlank(), ImUnanswered::getStatus, status)
                .orderByDesc(ImUnanswered::getHitCount)
                .orderByDesc(ImUnanswered::getUpdatedAt));
    }

    /** 标记未命中问题已补充话术。 */
    public void resolveUnanswered(Long id) {
        ImUnanswered exist = unansweredMapper.selectById(id);
        if (exist == null) {
            throw new BizException(Code.PARAM_INVALID, "未命中问题不存在");
        }
        ImUnanswered upd = new ImUnanswered();
        upd.setId(id);
        upd.setStatus(ImUnanswered.STATUS_RESOLVED);
        unansweredMapper.updateById(upd);
    }

    /** 内容拦截记录分页（可按会话过滤）。 */
    public IPage<ImContentBlock> blocksPage(String convId, int page, int size) {
        return blockMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<ImContentBlock>()
                .eq(convId != null && !convId.isBlank(), ImContentBlock::getConvId, convId)
                .orderByDesc(ImContentBlock::getCreatedAt));
    }

    /** 纠纷预警分页（unhandledOnly=true 仅看未跟进）。 */
    public IPage<ImDisputeWarning> warningsPage(boolean unhandledOnly, int page, int size) {
        return warningMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<ImDisputeWarning>()
                .eq(unhandledOnly, ImDisputeWarning::getHandledFlag, 0)
                .orderByDesc(ImDisputeWarning::getCreatedAt));
    }

    /** 标记预警已跟进。 */
    public void handleWarning(Long id, Long adminId) {
        ImDisputeWarning exist = warningMapper.selectById(id);
        if (exist == null) {
            throw new BizException(Code.PARAM_INVALID, "纠纷预警不存在");
        }
        ImDisputeWarning upd = new ImDisputeWarning();
        upd.setId(id);
        upd.setHandledFlag(1);
        upd.setHandledBy(adminId);
        warningMapper.updateById(upd);
    }

    /** 自动应答记录分页（可按来源/意图过滤，REQ-10 审计）。 */
    public IPage<ImAutoReply> repliesPage(String source, String intent, int page, int size) {
        return autoReplyMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<ImAutoReply>()
                .eq(source != null && !source.isBlank(), ImAutoReply::getSource, source)
                .eq(intent != null && !intent.isBlank(), ImAutoReply::getIntent, intent)
                .orderByDesc(ImAutoReply::getCreatedAt));
    }

    /** 质量统计（PRD §七 指标：自动解决率 / 转人工率 / 降级率 / 满意度 / 预警与拦截量）。 */
    public ImAssistantStatsVO stats() {
        long total = autoReplyMapper.selectCount(null);
        long escalated = autoReplyMapper.selectCount(new LambdaQueryWrapper<ImAutoReply>()
                .eq(ImAutoReply::getEscalated, 1));
        long knowledge = countSource(ImAutoReply.SOURCE_KNOWLEDGE);
        long llm = countSource(ImAutoReply.SOURCE_LLM);
        long fallback = countSource(ImAutoReply.SOURCE_TEMPLATE_FALLBACK);
        long escalation = countSource(ImAutoReply.SOURCE_ESCALATION);
        long useful = countFeedback(ImAutoReply.FEEDBACK_USEFUL);
        long useless = countFeedback(ImAutoReply.FEEDBACK_USELESS);
        long warnings = nz(warningMapper.selectCount(null));
        long unhandled = nz(warningMapper.selectCount(new LambdaQueryWrapper<ImDisputeWarning>()
                .eq(ImDisputeWarning::getHandledFlag, 0)));
        long blocks = nz(blockMapper.selectCount(null));
        long unanswered = nz(unansweredMapper.selectCount(new LambdaQueryWrapper<ImUnanswered>()
                .eq(ImUnanswered::getStatus, ImUnanswered.STATUS_PENDING)));

        ImAssistantStatsVO vo = new ImAssistantStatsVO();
        vo.setTotalReplies(total);
        vo.setEscalated(escalated);
        vo.setKnowledgeReplies(knowledge);
        vo.setLlmReplies(llm);
        vo.setFallbackReplies(fallback);
        vo.setEscalationNotices(escalation);
        vo.setUseful(useful);
        vo.setUseless(useless);
        vo.setWarnings(warnings);
        vo.setUnhandledWarnings(unhandled);
        vo.setBlocks(blocks);
        vo.setPendingUnanswered(unanswered);
        vo.setAutoResolveRate(total == 0 ? 0.0 : round2((double) (total - escalated) / total));
        vo.setEscalateRate(total == 0 ? 0.0 : round2((double) escalated / total));
        vo.setFallbackRate(total == 0 ? 0.0 : round2((double) fallback / total));
        long feedbackTotal = useful + useless;
        vo.setSatisfactionRate(feedbackTotal == 0 ? 0.0 : round2((double) useful / feedbackTotal));
        return vo;
    }

    // ==================== 内部 ====================

    /** 转人工结果（控制器据此以卖家侧身份发出提示消息）。 */
    public record EscalationResult(String convId, Long peerId, Long selfId, Long itemId,
                                   String notice, Long replyId, String summary) {
    }

    private List<String> escalate(Conversation conv, Long buyerId, Long sellerId, Long itemId,
                                  Long triggerMessageId, String text, EscalationPolicy.Decision decision) {
        metrics.increment(ImAssistantMetrics.ESCALATE_HUMAN + "." + decision.reason().name());
        if (decision.reason() == EscalationPolicy.Reason.LOW_CONFIDENCE) {
            // REQ-14：低置信度问题入未命中池，供运营补充话术
            addUnanswered(text, IntentDetector.detect(text), resolveCategory(itemId), conv.getConvId());
        }
        String summary = null;
        if (decision.takeover()) {
            markTakeover(conv);
            summary = buildSummary(conv, buyerId); // REQ-07 升级时会话摘要
        }
        String reply = AnswerComposer.compose(decision.notice(), AnswerComposer.Source.ESCALATION);
        recordReply(conv.getConvId(), itemId, buyerId, sellerId, triggerMessageId,
                IntentDetector.detect(text), ImAutoReply.SOURCE_ESCALATION, 0.0, text, reply, true,
                decision.reason().name(), summary);
        return List.of(reply);
    }

    private void markTakeover(Conversation conv) {
        Conversation upd = new Conversation();
        upd.setId(conv.getId());
        upd.setHumanTakeover(1);
        conversationMapper.updateById(upd);
        conv.setHumanTakeover(1); // 同步内存态，避免同一次链路内重复升级
    }

    /** 会话级刷量限流：单会话当日自动应答（含升级提示）达到上限即静默（边界场景 8）。 */
    private boolean rateLimited(String convId, int maxPerDay) {
        if (maxPerDay <= 0) {
            return false;
        }
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        Long count = autoReplyMapper.selectCount(new LambdaQueryWrapper<ImAutoReply>()
                .eq(ImAutoReply::getConvId, convId)
                .ge(ImAutoReply::getCreatedAt, startOfDay));
        return count != null && count >= maxPerDay;
    }

    /** 风险分是否仍在累计窗口内（超时则重新累计）。 */
    private boolean freshRisk(Conversation conv, int windowHours) {
        return conv.getDisputeRiskAt() != null
                && Duration.between(conv.getDisputeRiskAt(), LocalDateTime.now()).toHours() < windowHours;
    }

    private void persistRisk(Conversation conv, int riskScore) {
        Conversation upd = new Conversation();
        upd.setId(conv.getId());
        upd.setDisputeRiskScore(riskScore);
        upd.setDisputeRiskAt(LocalDateTime.now());
        conversationMapper.updateById(upd);
        conv.setDisputeRiskScore(riskScore);
        conv.setDisputeRiskAt(upd.getDisputeRiskAt());
    }

    /** 卖家超时未响应判定：最后一条消息来自买家且超过阈值时长（助手代答会刷新 updatedAt，天然排除）。 */
    private boolean slowReply(Conversation conv, int slowHours) {
        if (conv.getUpdatedAt() == null || conv.getLastSenderId() == null) {
            return false;
        }
        boolean fromBuyer = conv.getLastSenderId().equals(conv.getBuyerId());
        return fromBuyer && Duration.between(conv.getUpdatedAt(), LocalDateTime.now()).toHours() >= slowHours;
    }

    private String recordWarning(Conversation conv, Long buyerId, Long sellerId, int riskScore,
                                 Level level, List<Signal> signals) {
        ImDisputeWarning w = new ImDisputeWarning();
        w.setWarningNo(IdGenerator.disputeWarningNo());
        w.setConvId(conv.getConvId());
        w.setItemId(conv.getItemId());
        w.setBuyerId(buyerId);
        w.setSellerId(sellerId);
        w.setRiskScore(riskScore);
        w.setLevel(level == Level.HIGH ? ImDisputeWarning.LEVEL_HIGH : ImDisputeWarning.LEVEL_WARN);
        w.setSignals(signals.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse(""));
        String notice = level == Level.HIGH
                ? "系统检测到本会话纠纷风险较高（风险分 " + riskScore + "），已转接人工客服处理。"
                : "系统检测到本会话存在纠纷风险（风险分 " + riskScore + "）。如协商不顺，你可在会话页发起平台维权，由平台介入处理。";
        w.setNoticeText(notice);
        w.setHandledFlag(0);
        warningMapper.insert(w);
        return notice;
    }

    private void recordBlock(String convId, Long userId, String hitType, String text) {
        ImContentBlock b = new ImContentBlock();
        b.setConvId(convId);
        b.setUserId(userId);
        b.setHitType(hitType);
        b.setMatchedText(truncate(SensitiveInfoMasker.mask(text), 200));
        b.setAction("BLOCK_AND_ESCALATE");
        blockMapper.insert(b);
    }

    private Long recordReply(String convId, Long itemId, Long buyerId, Long sellerId, Long triggerMessageId,
                             ImIntent intent, String source, Double confidence, String question,
                             String content, boolean escalated, String escalateReason, String summary) {
        ImAutoReply r = new ImAutoReply();
        r.setReplyNo(IdGenerator.assistantReplyNo());
        r.setConvId(convId);
        r.setItemId(itemId);
        r.setBuyerId(buyerId);
        r.setSellerId(sellerId);
        r.setTriggerMessageId(triggerMessageId);
        r.setIntent(intent == null ? null : intent.name());
        r.setSource(source);
        r.setConfidence(confidence);
        r.setContent(truncate(content, 1000));
        r.setQuestion(truncate(SensitiveInfoMasker.mask(question), 250));
        r.setSummary(truncate(summary, 1000));
        r.setFeedback(ImAutoReply.FEEDBACK_NONE);
        r.setEscalated(escalated ? 1 : 0);
        r.setEscalateReason(escalateReason);
        autoReplyMapper.insert(r);
        return r.getId();
    }

    /** 未命中问题入池（同文本累加命中次数，REQ-14）。 */
    private void addUnanswered(String question, ImIntent intent, Long categoryId, String convId) {
        if (question == null || question.isBlank()) {
            return;
        }
        String masked = truncate(SensitiveInfoMasker.mask(question), 250);
        ImUnanswered exist = unansweredMapper.selectOne(new LambdaQueryWrapper<ImUnanswered>()
                .eq(ImUnanswered::getQuestion, masked)
                .last("LIMIT 1"));
        if (exist == null) {
            ImUnanswered u = new ImUnanswered();
            u.setQuestion(masked);
            u.setIntent(intent == null ? null : intent.name());
            u.setCategoryId(categoryId);
            u.setHitCount(1);
            u.setLastConvId(convId);
            u.setStatus(ImUnanswered.STATUS_PENDING);
            unansweredMapper.insert(u);
        } else {
            ImUnanswered upd = new ImUnanswered();
            upd.setId(exist.getId());
            upd.setHitCount((exist.getHitCount() == null ? 0 : exist.getHitCount()) + 1);
            upd.setLastConvId(convId);
            unansweredMapper.updateById(upd);
        }
        metrics.increment(ImAssistantMetrics.UNANSWERED_POOL);
    }

    /** LLM 用户消息：问题 + 类目 + 知识库上下文（幻觉约束：答案须绑条目，REQ 边界场景）。 */
    private String buildLlmUserMessage(String text, Long categoryId, KnowledgeMatcher.MatchResult match) {
        StringBuilder sb = new StringBuilder();
        sb.append("买家问题：").append(text == null ? "" : text).append('\n');
        sb.append("商品类目ID：").append(categoryId == null ? "未知" : categoryId).append('\n');
        if (match.hit()) {
            sb.append("可参考知识库条目：\n1. ").append(match.candidate().answerTemplate()).append('\n');
        } else {
            sb.append("可参考知识库条目：（无）\n");
        }
        sb.append("要求：仅依据以上条目与平台规则作答；不得代替卖家接受议价或承诺价格，"
                + "不得对退款/责任/赔付作判定；无法确定时明确说明并建议转人工。");
        return sb.toString();
    }

    /** 会话摘要（REQ-07）：买家诉求 + 已自动答复 + 证据引用。 */
    private String buildSummary(Conversation conv, Long buyerId) {
        List<Message> msgs = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getConvId, conv.getConvId())
                .orderByDesc(Message::getCreatedAt)
                .last("LIMIT " + SUMMARY_MSG_LIMIT));
        List<ConversationSummarizer.Msg> views = new ArrayList<>();
        for (int i = msgs.size() - 1; i >= 0; i--) { // 反转为时间正序
            Message m = msgs.get(i);
            boolean fromAssistant = m.getContent() != null
                    && m.getContent().startsWith(AnswerComposer.ASSISTANT_PREFIX);
            views.add(new ConversationSummarizer.Msg(m.getSenderId(), fromAssistant, m.getContent(),
                    m.getType(), m.getSeq(), m.getCreatedAt() == null ? null
                    : m.getCreatedAt().format(com.idlefish.trade.common.util.DateTimeUtil.FMT)));
        }
        return ConversationSummarizer.summarize(conv.getItemId(), buyerId, views, MAX_SNIPPETS).toText();
    }

    private Long resolveCategory(Long itemId) {
        if (itemId == null) {
            return null;
        }
        try {
            Item item = itemService.view(itemId);
            return item == null ? null : item.getCategoryId();
        } catch (Exception e) {
            log.debug("[im-assistant] 商品查询失败（类目回落通用话术） itemId={}", itemId);
            return null;
        }
    }

    private Conversation loadConv(String convId) {
        Conversation conv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getConvId, convId));
        if (conv == null) {
            throw new BizException(Code.CONV_NOT_FOUND);
        }
        return conv;
    }

    private void requireParticipant(Conversation conv, Long userId) {
        if (!userId.equals(conv.getBuyerId()) && !userId.equals(conv.getSellerId())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "无权访问该会话");
        }
    }

    private Long peerOf(Conversation conv, Long userId) {
        return userId.equals(conv.getBuyerId()) ? conv.getSellerId() : conv.getBuyerId();
    }

    private long countSource(String source) {
        Long c = autoReplyMapper.selectCount(new LambdaQueryWrapper<ImAutoReply>()
                .eq(ImAutoReply::getSource, source));
        return c == null ? 0L : c;
    }

    private long countFeedback(String feedback) {
        Long c = autoReplyMapper.selectCount(new LambdaQueryWrapper<ImAutoReply>()
                .eq(ImAutoReply::getFeedback, feedback));
        return c == null ? 0L : c;
    }

    private static ImIntent parseIntentOrNull(String intent) {
        if (intent == null || intent.isBlank()) {
            return null;
        }
        try {
            return ImIntent.valueOf(intent);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
