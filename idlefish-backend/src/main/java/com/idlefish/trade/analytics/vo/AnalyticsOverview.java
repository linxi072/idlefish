package com.idlefish.trade.analytics.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 运营概览（F-15.5）。金额字段单位：分。
 */
@Data
public class AnalyticsOverview {

    /** 周期内 GMV（分）。 */
    private Long gmv;
    /** 周期内支付订单数（paid/pending_ship/shipping/completed）。 */
    private Long paidOrderCount;
    /** 周期内订单总数。 */
    private Long totalOrderCount;
    /** 周期内退款订单数（refunding/refunded）。 */
    private Long refundOrderCount;
    /** 退款率 = refundOrderCount / paidOrderCount（0~1）。 */
    private Double refundRate;
    /** 客单价（分）= gmv / paidOrderCount。 */
    private Long avgOrderValue;
    /** 累计注册用户数。 */
    private Long userCount;
    /** 在售商品数。 */
    private Long itemOnsaleCount;
    /** 商品总数（未删）。 */
    private Long itemTotalCount;
    /** 每日趋势明细。 */
    private List<Map<String, Object>> trend;
}
