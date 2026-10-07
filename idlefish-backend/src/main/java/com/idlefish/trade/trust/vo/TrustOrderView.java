package com.idlefish.trade.trust.vo;

import com.idlefish.trade.trade.vo.OrderLogisticsVO;
import com.idlefish.trade.trust.TrustRules;
import lombok.Data;

import java.util.List;

/**
 * 订单详情信任视图（U02 REQ-05/07 + 保障摘要）。
 */
@Data
public class TrustOrderView {

    /** REQ-07 物流时间轴（未发货为 null）。 */
    private OrderLogisticsVO logistics;

    /** REQ-05 售后进度（无工单时 hasDispute=false）。 */
    private AftersaleProgress aftersale;

    /** REQ-03 交易保障摘要。 */
    private String guaranteeSummary;

    /** REQ-11 保障边界。 */
    private String guaranteeBoundary;

    /** REQ-05 售后进度可视化。 */
    @Data
    public static class AftersaleProgress {
        private boolean hasDispute;
        private String currentStatus;     // 工单当前状态
        private String currentStatusLabel;
        private String result;            // 裁决结果（如有）
        private List<TrustRules.ProgressNode> nodes;
    }
}
