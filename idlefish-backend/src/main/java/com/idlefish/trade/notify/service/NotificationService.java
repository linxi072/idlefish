package com.idlefish.trade.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.notify.channel.ChannelType;
import com.idlefish.trade.notify.channel.NotifyChannel;
import com.idlefish.trade.notify.channel.NotifyMessage;
import com.idlefish.trade.notify.entity.Notification;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.mapper.NotificationMapper;
import com.idlefish.trade.notify.vo.NotificationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 站内通知服务（F-02 通知中心 + F-05 事件通知闭环）。
 * <p>
 * 发送为编排层：按事件类型路由到多个触达渠道（IN_APP/PUSH/SMS），各渠道 best-effort，
 * 任一渠道失败不影响其他渠道与主业务流程。读取（列表/未读/已读）仍基于 t_notification。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationMapper notificationMapper;
    private final List<NotifyChannel> channels;

    public NotificationService(NotificationMapper notificationMapper, List<NotifyChannel> channels) {
        this.notificationMapper = notificationMapper;
        this.channels = channels;
    }

    /**
     * 发送通知（best-effort）：按 type 路由到多个渠道分发；任一渠道异常仅记录日志，绝不向调用方抛出。
     */
    public void notify(Long userId, NotificationType type, String bizId, String title, String content) {
        if (userId == null) {
            return;
        }
        NotifyMessage msg = NotifyMessage.builder()
                .userId(userId).type(type).bizId(bizId).title(title).content(content)
                .channels(resolveChannels(type)).build();
        for (NotifyChannel ch : channels) {
            if (msg.getChannels() != null && msg.getChannels().contains(ch.type())) {
                try {
                    ch.send(msg);
                } catch (Exception e) {
                    log.warn("[notify] 渠道 {} 发送失败 userId={} type={}", ch.type(), userId, type, e);
                }
            }
        }
    }

    /** 事件类型 → 渠道路由策略。紧急事件（平台介入）追加短信；其余走站内信 + 实时推送。 */
    List<ChannelType> resolveChannels(NotificationType type) {
        if (type == NotificationType.REFUND_PLATFORM || type == NotificationType.SYSTEM_ALERT) {
            return Arrays.asList(ChannelType.IN_APP, ChannelType.PUSH, ChannelType.SMS);
        }
        return Arrays.asList(ChannelType.IN_APP, ChannelType.PUSH);
    }

    /** 我的通知列表（分页，按时间倒序）。 */
    public IPage<NotificationVO> list(Long userId, int page, int size) {
        Page<Notification> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        IPage<Notification> result = notificationMapper.selectPage(p, new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId).orderByDesc(Notification::getCreatedAt));
        return result.convert(this::toVO);
    }

    /** 未读数量。 */
    public long unreadCount(Long userId) {
        Long c = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId).eq(Notification::getRead, 0));
        return c == null ? 0 : c;
    }

    /** 标记单条已读（仅本人）。 */
    public void markRead(Long userId, Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new BizException(Code.NOT_FOUND, "通知不存在");
        }
        Notification upd = new Notification();
        upd.setId(id);
        upd.setRead(1);
        notificationMapper.updateById(upd);
    }

    /** 全部已读（仅本人），返回更新条数。 */
    public int markAllRead(Long userId) {
        return notificationMapper.update(new Notification(),
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getRead, 0)
                        .set(Notification::getRead, 1));
    }

    private NotificationVO toVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setType(n.getType());
        vo.setBizId(n.getBizId());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setRead(n.getRead());
        vo.setCreatedAt(n.getCreatedAt() == null ? null : n.getCreatedAt().format(FMT));
        return vo;
    }
}
