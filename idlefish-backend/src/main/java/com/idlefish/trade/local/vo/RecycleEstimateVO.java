package com.idlefish.trade.local.vo;

import lombok.Data;

/**
 * 回收估价视图（ITER-F05 / REQ-02）：供回收申请页「预估回收价」展示。
 * 金额恒「分」。hasData=false 时前端隐藏预估价区块，不做虚假报价。
 */
@Data
public class RecycleEstimateVO {

    /** 是否有可用估价。 */
    private boolean hasData;

    /** 参考价来源：input(用户/运营指定) / pricing(ITER-F03 估价引擎) / null(无数据)。 */
    private String source;

    /** 参考价（分）。 */
    private long referenceFen;

    /** 预估回收价（分）：参考价 × 成色系数，受上下限保护。 */
    private long estimateFen;

    /** 成色档位。 */
    private int conditionLevel;

    /** 预计上门/自送方式是否可用（城市与品类通过）。 */
    private boolean recyclable;

    /** 不可用原因（如「该城市暂未开通上门回收」）。 */
    private String unavailableReason;

    public static RecycleEstimateVO noData(String reason) {
        RecycleEstimateVO v = new RecycleEstimateVO();
        v.setHasData(false);
        v.setRecyclable(false);
        v.setUnavailableReason(reason);
        return v;
    }
}
