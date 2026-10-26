package com.idlefish.trade.im.assistant;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话摘要纯函数（ITER-F06 / REQ-07 升级时会话摘要）。
 * <p>买家转人工 / 纠纷预警时，将会话压缩为一段结构化文本随工单一并送达人工客服：
 * 商品、买家诉求（最多 N 条）、已自动答复（最多 N 条）、证据引用（图片消息序号）、
 * 消息规模与最近活跃时间。纯函数、无副作用。</p>
 */
public final class ConversationSummarizer {

    private ConversationSummarizer() {
    }

    /** 摘要入参消息视图（由 Message 实体映射，保持纯函数不依赖实体）。 */
    public record Msg(Long senderId, boolean fromAssistant, String content, String type, Long seq, String createdAt) {
    }

    /** 摘要结果。 */
    public record Summary(Long itemId, int messageCount, int buyerCount, int sellerCount, int assistantCount,
                          List<String> buyerRequests, List<String> autoAnswered, List<String> evidenceRefs,
                          String lastActiveAt) {

        /** 渲染为人工客服可读的多行文本。 */
        public String toText() {
            StringBuilder sb = new StringBuilder();
            sb.append("【会话摘要】商品ID=").append(itemId == null ? "-" : itemId).append('\n');
            sb.append("消息规模：共 ").append(messageCount).append(" 条（买家 ").append(buyerCount)
                    .append(" / 卖家 ").append(sellerCount).append(" / 智能助手 ").append(assistantCount).append("）\n");
            sb.append("买家诉求：").append(buyerRequests.isEmpty() ? "（无）" : String.join("；", buyerRequests)).append('\n');
            sb.append("已自动答复：").append(autoAnswered.isEmpty() ? "（无）" : String.join("；", autoAnswered)).append('\n');
            sb.append("证据引用：").append(evidenceRefs.isEmpty() ? "（无）" : String.join("；", evidenceRefs)).append('\n');
            sb.append("最近活跃：").append(lastActiveAt == null ? "-" : lastActiveAt);
            return sb.toString();
        }
    }

    /** 单条摘要片段最大长度。 */
    private static final int SNIPPET_MAX = 40;

    /**
     * 生成会话摘要。
     *
     * @param itemId      商品 ID（平台客服场景可为空）
     * @param buyerId     买家用户 ID（用于区分买卖家消息；为空时不区分，全部计为买家）
     * @param messages    按时间正序的会话消息
     * @param maxSnippets 诉求/答复各保留的最大条数（防摘要过长）
     */
    public static Summary summarize(Long itemId, Long buyerId, List<Msg> messages, int maxSnippets) {
        if (messages == null || messages.isEmpty()) {
            return new Summary(itemId, 0, 0, 0, 0, List.of(), List.of(), List.of(), null);
        }
        int buyer = 0;
        int seller = 0;
        int assistant = 0;
        List<String> requests = new ArrayList<>();
        List<String> answered = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        String lastActive = null;
        for (Msg m : messages) {
            if (m.fromAssistant()) {
                assistant++;
                if (answered.size() < maxSnippets) {
                    answered.add(snippet(m.content()));
                }
            } else if (buyerId != null && m.senderId() != null && !buyerId.equals(m.senderId())) {
                seller++;
            } else {
                buyer++;
                if (requests.size() < maxSnippets && m.content() != null && !m.content().isBlank()) {
                    requests.add(snippet(m.content()));
                }
            }
            if ("image".equals(m.type())) {
                evidence.add("消息#" + (m.seq() == null ? "?" : m.seq()) + "[图片]");
            }
            if (m.createdAt() != null) {
                lastActive = m.createdAt();
            }
        }
        return new Summary(itemId, messages.size(), buyer, seller, assistant,
                List.copyOf(requests), List.copyOf(answered), List.copyOf(evidence), lastActive);
    }

    private static String snippet(String content) {
        if (content == null) {
            return "";
        }
        String s = content.trim();
        return s.length() <= SNIPPET_MAX ? s : s.substring(0, SNIPPET_MAX) + "…";
    }
}
