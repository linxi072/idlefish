package com.idlefish.trade.publish.vo;

import lombok.Data;

/**
 * REQ-02 草稿建议（标题/描述）。
 */
@Data
public class DraftSuggestion {
    /** 数据来源：ai / rule。 */
    private String source;
    private String title;
    private String description;
    /** AI 是否可用（用于埋点 ai_suggest_adopt 口径）。 */
    private boolean aiAvailable;
}
