package com.idlefish.trade.meta.vo;

/**
 * 业务术语表条目：统一跨端命名，避免「担保交易 / 验货 / 议价」等术语在三端表述不一致。
 *
 * @param term       标准术语
 * @param definition 定义（一句话）
 * @param synonym    常见别名 / 口语（多端对齐时优先采用 term）
 * @param domain     所属业务域（trade / item / marketing / risk / platform）
 */
public record TermGlossaryVO(String term, String definition, String synonym, String domain) {
}
