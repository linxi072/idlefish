package com.idlefish.trade.workorder.dto;

import java.time.LocalDateTime;

/**
 * 工单关联的维权纠纷简要（F-12 补完：工单与维权纠纷联通）。
 *
 * <p>只暴露定位与进度所需字段，不含买卖双方举证图片（避免通过工单接口绕过维权详情的归属校验）。
 */
public record WorkOrderLinkedDisputeVO(String disputeNo,
                                       String orderNo,
                                       String status,
                                       String type,
                                       String expectation,
                                       Long amount,
                                       String result,
                                       Long refundAmount,
                                       LocalDateTime createdAt) {

    public static WorkOrderLinkedDisputeVO empty() {
        return new WorkOrderLinkedDisputeVO(null, null, null, null, null, null, null, null, null);
    }
}
