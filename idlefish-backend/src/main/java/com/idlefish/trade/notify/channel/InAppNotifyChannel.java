package com.idlefish.trade.notify.channel;

import com.idlefish.trade.notify.entity.Notification;
import com.idlefish.trade.notify.mapper.NotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 站内信渠道（默认落地渠道）：将通知落库 t_notification。
 * 任何异常仅记录日志，不影响主流程。落库逻辑由 NotificationService 编排层下沉至此。
 */
@Slf4j
@Component
public class InAppNotifyChannel implements NotifyChannel {

    private final NotificationMapper notificationMapper;

    public InAppNotifyChannel(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Override
    public ChannelType type() {
        return ChannelType.IN_APP;
    }

    @Override
    public void send(NotifyMessage msg) {
        if (msg.getUserId() == null) {
            return;
        }
        try {
            Notification n = new Notification();
            n.setUserId(msg.getUserId());
            n.setType(msg.getType() == null ? null : msg.getType().getCode());
            n.setBizId(msg.getBizId());
            n.setTitle(msg.getTitle());
            n.setContent(msg.getContent());
            n.setRead(0);
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.error("[notify:in-app] 落库失败 userId={} type={}", msg.getUserId(), msg.getType(), e);
        }
    }
}
