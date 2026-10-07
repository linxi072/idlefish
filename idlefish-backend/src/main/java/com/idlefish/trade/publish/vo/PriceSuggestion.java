package com.idlefish.trade.publish.vo;

import lombok.Data;

/**
 * REQ-04 估价建议（参考值，单位：分）。
 */
@Data
public class PriceSuggestion {
    /** 是否有可展示的估价数据（无 AI 且无基准价时为 false，前端不展示区间）。 */
    private boolean hasData;
    /** 数据来源：ai / rule / null。 */
    private String source;
    private long minFen;
    private long maxFen;
    /** REQ-09 免责：估价区间仅供参考，最终定价由卖家决定。 */
    private String note = "估价区间为系统参考建议，最终定价由卖家决定（REQ-09）";
}
