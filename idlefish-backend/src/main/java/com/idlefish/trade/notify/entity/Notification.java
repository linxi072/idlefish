package com.idlefish.trade.notify.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内通知（F-02 通知中心）。订单/支付/退款/审核等事件触达用户；
 * 每条通知归属一个用户，标记是否已读。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_notification")
public class Notification extends BaseEntity {

    private Long userId;
    private String type;            // 通知类型 code，见 NotificationType
    private String bizId;           // 业务主键，如订单号/退款单号/商品ID
    private String title;
    private String content;
    @TableField("is_read")
    private Integer read;           // 0 未读 / 1 已读
}
