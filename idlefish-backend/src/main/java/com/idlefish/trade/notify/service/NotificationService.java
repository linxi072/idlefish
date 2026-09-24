package com.idlefish.trade.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.notify.entity.Notification;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.mapper.NotificationMapper;
import com.idlefish.trade.notify.vo.NotificationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * 站内通知服务（F-02 通知中心）。
 * 发送为 best-effort：任何异常仅记录日志并吞掉，绝不向调用方抛出，避免影响主业务流程。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /** 发送站内通知（best-effort）。userId 为空时直接忽略。 */
    public void notify(Long userId, NotificationType type, String bizId, String title, String content) {
        if (userId == null) {
            return;
        }
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type.getCode());
            n.setBizId(bizId);
            n.setTitle(title);
            n.setContent(content);
            n.setRead(0);
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.error("[notify] 发送站内通知失败 userId={} type={} bizId={}", userId, type, bizId, e);
        }
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
