package com.idlefish.trade.im.assistant;

/**
 * LLM 客户端抽象（ITER-F06 / REQ-12 LLM 不可用回落模板不阻塞）。
 * <p>默认实现为 OpenAI 兼容真实 HTTP 调用；任何失败（未配置 / 超时 / 非 2xx / 空内容）
 * 一律返回 {@code null}，由上层回落通用模板并记录降级埋点，绝不阻塞买家消息主流程。</p>
 */
public interface LlmClient {

    /** 是否可用（开关打开且配置齐全）。 */
    boolean isAvailable();

    /**
     * 生成回答。
     *
     * @param systemPrompt 系统提示词（幻觉约束）
     * @param userMessage  用户消息（含问题与知识库上下文）
     * @return 回答文本；不可用或失败返回 {@code null}
     */
    String chat(String systemPrompt, String userMessage);
}
