package com.idlefish.trade.im.vo;

/**
 * IM 智能助手质量统计（ITER-F06 / PRD §七 指标）。
 * <p>对应指标：自动解决率（autoResolveRate）、转人工率（escalateRate）、
 * 降级率（fallbackRate）、满意度（satisfactionRate），以及预警/拦截/未命中存量。</p>
 */
public class ImAssistantStatsVO {

    /** 自动应答记录总数（含转人工提示）。 */
    private long totalReplies;
    /** 其中转人工的记录数。 */
    private long escalated;
    private long knowledgeReplies;
    private long llmReplies;
    private long fallbackReplies;
    private long escalationNotices;
    private long useful;
    private long useless;
    private long warnings;
    private long unhandledWarnings;
    private long blocks;
    private long pendingUnanswered;
    /** 自动解决率 = (总数 - 转人工) / 总数，目标 ≥60%。 */
    private double autoResolveRate;
    /** 转人工率，目标 ≤35%。 */
    private double escalateRate;
    /** 模板降级率，目标 <3%。 */
    private double fallbackRate;
    /** 满意度 = 有用 / (有用 + 没用)，目标 ≥70%。 */
    private double satisfactionRate;

    public long getTotalReplies() {
        return totalReplies;
    }

    public void setTotalReplies(long totalReplies) {
        this.totalReplies = totalReplies;
    }

    public long getEscalated() {
        return escalated;
    }

    public void setEscalated(long escalated) {
        this.escalated = escalated;
    }

    public long getKnowledgeReplies() {
        return knowledgeReplies;
    }

    public void setKnowledgeReplies(long knowledgeReplies) {
        this.knowledgeReplies = knowledgeReplies;
    }

    public long getLlmReplies() {
        return llmReplies;
    }

    public void setLlmReplies(long llmReplies) {
        this.llmReplies = llmReplies;
    }

    public long getFallbackReplies() {
        return fallbackReplies;
    }

    public void setFallbackReplies(long fallbackReplies) {
        this.fallbackReplies = fallbackReplies;
    }

    public long getEscalationNotices() {
        return escalationNotices;
    }

    public void setEscalationNotices(long escalationNotices) {
        this.escalationNotices = escalationNotices;
    }

    public long getUseful() {
        return useful;
    }

    public void setUseful(long useful) {
        this.useful = useful;
    }

    public long getUseless() {
        return useless;
    }

    public void setUseless(long useless) {
        this.useless = useless;
    }

    public long getWarnings() {
        return warnings;
    }

    public void setWarnings(long warnings) {
        this.warnings = warnings;
    }

    public long getUnhandledWarnings() {
        return unhandledWarnings;
    }

    public void setUnhandledWarnings(long unhandledWarnings) {
        this.unhandledWarnings = unhandledWarnings;
    }

    public long getBlocks() {
        return blocks;
    }

    public void setBlocks(long blocks) {
        this.blocks = blocks;
    }

    public long getPendingUnanswered() {
        return pendingUnanswered;
    }

    public void setPendingUnanswered(long pendingUnanswered) {
        this.pendingUnanswered = pendingUnanswered;
    }

    public double getAutoResolveRate() {
        return autoResolveRate;
    }

    public void setAutoResolveRate(double autoResolveRate) {
        this.autoResolveRate = autoResolveRate;
    }

    public double getEscalateRate() {
        return escalateRate;
    }

    public void setEscalateRate(double escalateRate) {
        this.escalateRate = escalateRate;
    }

    public double getFallbackRate() {
        return fallbackRate;
    }

    public void setFallbackRate(double fallbackRate) {
        this.fallbackRate = fallbackRate;
    }

    public double getSatisfactionRate() {
        return satisfactionRate;
    }

    public void setSatisfactionRate(double satisfactionRate) {
        this.satisfactionRate = satisfactionRate;
    }
}
