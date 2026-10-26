package com.idlefish.trade.im.assistant;

import java.util.List;

/**
 * 知识库话术匹配纯函数（ITER-F06 / REQ-11 品类话术优先）。
 * <p>入参为不可变 {@link Candidate} 视图（不耦合实体，便于离线单测）：
 * 先按意图过滤，再按「关键词命中数 + 品类优先 + 运营优先级」打分取最优，
 * 置信度随命中数与品类命中提升。未命中返回 {@link MatchResult#NONE}。</p>
 */
public final class KnowledgeMatcher {

    private KnowledgeMatcher() {
    }

    /** 知识库条目候选视图（纯函数入参）。 */
    public record Candidate(Long id, Long categoryId, ImIntent intent, List<String> keywords,
                            String answerTemplate, int priority) {
    }

    /** 匹配结果：命中条目 + 置信度 + 是否品类优先命中。 */
    public record MatchResult(Candidate candidate, double confidence, boolean categoryHit) {

        /** 未命中哨兵。 */
        public static final MatchResult NONE = new MatchResult(null, 0.0, false);

        public boolean hit() {
            return candidate != null;
        }
    }

    /** 品类话术加分与命中数权重。 */
    private static final int HIT_WEIGHT = 100;
    private static final int CATEGORY_WEIGHT = 50;

    /**
     * 匹配最优话术条目。
     *
     * @param candidates 启用中的知识库条目（运营后台维护）
     * @param intent     识别出的买家意图（UNKNOWN 直接不命中）
     * @param categoryId 商品类目 ID（为空则仅走通用话术）
     * @param text       买家消息原文
     */
    public static MatchResult match(List<Candidate> candidates, ImIntent intent, Long categoryId, String text) {
        if (candidates == null || candidates.isEmpty() || intent == null || intent == ImIntent.UNKNOWN) {
            return MatchResult.NONE;
        }
        String folded = text == null ? "" : text.toLowerCase();
        Candidate best = null;
        int bestHits = 0;
        boolean bestCategory = false;
        int bestScore = -1;
        for (Candidate c : candidates) {
            if (c == null || c.intent() != intent) {
                continue;
            }
            int hits = 0;
            if (c.keywords() != null) {
                for (String kw : c.keywords()) {
                    if (kw != null && !kw.isBlank() && folded.contains(kw.toLowerCase())) {
                        hits++;
                    }
                }
            }
            if (hits == 0) {
                continue;
            }
            boolean categoryHit = categoryId != null && c.categoryId() != null && categoryId.equals(c.categoryId());
            int score = hits * HIT_WEIGHT + (categoryHit ? CATEGORY_WEIGHT : 0) + c.priority();
            if (score > bestScore) {
                best = c;
                bestHits = hits;
                bestCategory = categoryHit;
                bestScore = score;
            }
        }
        if (best == null) {
            return MatchResult.NONE;
        }
        return new MatchResult(best, confidence(bestHits, bestCategory), bestCategory);
    }

    /**
     * 置信度：单词命中 0.55，每个额外命中 +0.2（封顶 3 个，即 hits≥3 时 0.95 封顶），
     * 品类优先再 +0.2，整体封顶 0.95。
     * 与 {@link ConfidencePolicy} 默认阈值 0.6 配合：单词命中的通用话术（0.55）不自动作答，
     * 需 LLM 试探或转人工；双关键词命中（0.75）或品类命中（0.95）才自动作答。
     */
    static double confidence(int hits, boolean categoryHit) {
        double c = 0.55 + 0.2 * Math.max(0, hits - 1) + (categoryHit ? 0.2 : 0.0);
        return Math.min(0.95, c);
    }
}
