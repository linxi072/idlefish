package com.idlefish.trade.analytics.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 转化漏斗单级（F-15.5）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsFunnelStage {

    /** 阶段名称（商品浏览/搜索/收藏/下单/支付）。 */
    private String stage;
    /** 该阶段事件计数。 */
    private Long count;
    /** 环比上一级转化率（0~1）；首级为 1.0。 */
    private Double stepRate;
    /** 相对首级（浏览）整体转化率（0~1）。 */
    private Double overallRate;
}
