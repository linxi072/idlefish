package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评价（F-06）：订单完成后买卖双方互评。无外键约束，按 order_no / target_id 关联查询。
 * status：0 待审 / 1 通过 / 2 驳回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_review")
public class Review extends BaseEntity {

    private String orderNo;
    private Long itemId;
    private Long reviewerId;
    private Long targetId;
    private String role;       // ReviewRole.code
    private Integer rating;    // 1-5
    private String content;
    private Integer status;    // 0 待审 / 1 通过 / 2 驳回
    private Integer anonymous;
    private String rejectReason;
}
