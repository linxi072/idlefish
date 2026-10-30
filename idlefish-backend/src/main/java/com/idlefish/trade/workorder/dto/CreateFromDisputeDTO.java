package com.idlefish.trade.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 维权单转人工客服工单请求（F-12 补完：工单与维权纠纷联通）。
 *
 * <p>仅 {@code disputeNo} 与 {@code channel} 为必填：工单类型/优先级/标题/订单号均由维权单推导
 * （见 {@link com.idlefish.trade.workorder.WorkOrderDisputeLink}），避免前端伪造优先级抢占客服资源。
 * {@code description} 可选，缺省时由维权诉求自动生成。
 */
public class CreateFromDisputeDTO {

    @NotBlank(message = "维权工单号不能为空")
    private String disputeNo;

    @NotNull(message = "来源渠道不能为空")
    private String channel;

    /** 补充描述（可选）。 */
    private String description;

    public String getDisputeNo() {
        return disputeNo;
    }

    public void setDisputeNo(String disputeNo) {
        this.disputeNo = disputeNo;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
