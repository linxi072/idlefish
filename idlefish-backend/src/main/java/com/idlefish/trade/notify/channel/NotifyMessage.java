package com.idlefish.trade.notify.channel;

import com.idlefish.trade.notify.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 通知消息载体：由事件方构造，经 {@link com.idlefish.trade.notify.service.NotificationService} 路由后分发到各渠道。
 */
@Data
@Builder
public class NotifyMessage {

    /** 接收用户 ID（空则整条消息被忽略）。 */
    private Long userId;
    /** 事件类型，持久化用于展示与分类。 */
    private NotificationType type;
    /** 业务主键，如订单号/退款单号/结算单号。 */
    private String bizId;
    private String title;
    private String content;
    /** 指定渠道；为空则由 NotificationService 按类型默认路由。 */
    private List<ChannelType> channels;
    /** 推送/扩展用的额外 JSON（如未读数、跳转路径），可空。 */
    private String payload;
}
