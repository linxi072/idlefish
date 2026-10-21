package com.idlefish.trade.pricing.vo;

import lombok.Data;

/**
 * 偏离判定视图（ITER-F03）：议价页/挂价校验时提示报价是否显著偏离建议区间（REQ-09）。
 * 仅提示，不阻断。deviation ∈ {NONE, LOW, HIGH}。
 */
@Data
public class PriceDeviationVO {

    /** 是否有可用估价（无数据时偏离恒为 NONE 且 hasData=false）。 */
    private boolean hasData;
    /** 偏离类型：NONE / LOW / HIGH。 */
    private String deviation;
    /** 建议区间下限（分）。 */
    private long minFen;
    /** 建议区间中位（分）。 */
    private long medianFen;
    /** 建议区间上限（分）。 */
    private long maxFen;
}
