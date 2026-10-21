package com.idlefish.trade.pricing.vo;

import lombok.Data;

/**
 * 估价结果视图（ITER-F03）：供用户端估价气泡/议价参考卡展示（REQ-02/REQ-04）。
 * 金额单位：分（REQ-01）。hasData=false 时前端隐藏估价入口（REQ-06 降级）。
 */
@Data
public class PriceEstimateVO {

    /** 是否有可用估价：false 时前端隐藏入口，不阻塞发布/议价。 */
    private boolean hasData;
    /** 数据来源：sample(样本分位) / rule(规则兜底) / null(无数据)。 */
    private String source;
    /** 建议区间下限（分）。 */
    private long minFen;
    /** 建议区间中位/合理价（分）。 */
    private long medianFen;
    /** 建议区间上限（分）。 */
    private long maxFen;
    /** 参与估价的同成色样本数（规则兜底时为回落前的样本数，可为 0）。 */
    private int sampleCount;
    /** 口径说明（如「参考样本不足，采用规则兜底」「估价暂不可用」）。 */
    private String note;
}
