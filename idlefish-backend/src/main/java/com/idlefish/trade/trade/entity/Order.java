package com.idlefish.trade.trade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单（PRD §4.3）。金额单位：分。状态见 {@link com.idlefish.trade.common.enums.OrderStatus}。
 * 表名 orders（order 为 SQL 保留字）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order")
public class Order extends BaseEntity {

    private String orderNo;
    private Long buyerId;
    private Long sellerId;
    private Long itemId;
    private String skuSnapshot;     // JSON：标题/封面/单价等
    private Integer quantity;
    private Long unitPrice;         // 分
    private Long totalAmount;       // 分
    private Long freight;           // 分
    private Long payAmount;         // 分 = totalAmount + freight
    private String status;          // OrderStatus.code
    private String addressSnapshot; // JSON：收货人/电话/地址
    private String remark;
    private String payNo;
    private String logisticsNo;
    private String closeType;       // timeout / cancel
    private Long userCouponId;      // 使用的用户券 ID（t_user_coupon.id）
    private Long discountAmount;    // 优惠券抵扣金额（分）

    @Version
    private Integer version;
}
