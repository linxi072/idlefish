package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动参与记录（F-13.3）：每个用户每次参与落一条。
 * 唯一索引 (activity_id, user_id) 兜底并发重复参与；order_no 关联下单后锁定的订单。
 * status: JOINED(已参与未下单) / PAID_PENDING(已下单锁库存) / CANCELLED(已释放) / COMPLETED(已成团完成)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_activity_participant")
public class ActivityParticipant extends BaseEntity {

    private Long activityId;
    private Long userId;
    private Long groupId;         // 所属拼团（GROUP）；秒杀为 null
    private String orderNo;       // 关联订单号（下单锁定库存后回写）
    private String status;
    private Integer qty;          // 参与数量
}
