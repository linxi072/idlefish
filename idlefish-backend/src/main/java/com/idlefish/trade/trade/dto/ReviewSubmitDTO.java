package com.idlefish.trade.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 提交评价入参（F-06）。
 */
@Data
public class ReviewSubmitDTO {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @Min(1)
    @Max(5)
    private Integer rating;

    @Size(max = 512, message = "评价内容过长")
    private String content;

    /** 是否匿名：0 否 / 1 是。 */
    private Integer anonymous;
}
