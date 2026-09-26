package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 促销活动（F-13.3 拼团/限时秒杀）：围绕单个商品的限时营销。
 * type: SECKILL(秒杀) / GROUP(拼团)；金额单位：分。
 * 库存通过 {@code UPDATE ... SET stock = stock - ? WHERE id = ? AND stock >= ?} 原子扣减防超卖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_activity")
public class Activity extends BaseEntity {

    private Long itemId;          // 活动商品
    private String type;          // SECKILL / GROUP
    private Long activityPrice;   // 活动价（分）
    private Integer stock;        // 活动库存（独立于商品库存，营销配额）
    private Integer soldCount;    // 已锁定/已售数量
    private Integer limitPerUser; // 每人限购（<=0 表示不限）
    private Integer groupSize;    // 拼团成团人数（GROUP）
    private Integer groupValidMinutes; // 拼团有效分钟（GROUP）
    private String status;        // PENDING / ONGOING / ENDED
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
