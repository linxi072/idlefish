package com.idlefish.trade.local.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 质检调价入参（ITER-F05 / REQ-04）：质检后的最终报价，需经服务端封顶后进入待确认态。
 * 金额恒「分」。
 */
@Data
public class RecycleQuoteRequest {

    /** 最终回收报价（分）。 */
    @Min(value = 0, message = "报价不能为负")
    private Long finalAmount;

    /** 调价说明（对用户明示，REQ-04）。 */
    private String quoteRemark;
}
