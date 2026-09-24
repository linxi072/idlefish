package com.idlefish.trade.notify.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.notify.vo.NotificationVO;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口（需登录，按当前用户隔离）。
 */
@RestController
@RequestMapping("/api/notify")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 我的通知列表（分页）。 */
    @GetMapping("/list")
    public Result<IPage<NotificationVO>> list(@CurrentUser LoginUser user,
                                             @RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(notificationService.list(user.getUserId(), page, size));
    }

    /** 未读数量。 */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(@CurrentUser LoginUser user) {
        return Result.ok(notificationService.unreadCount(user.getUserId()));
    }

    /** 标记单条已读。 */
    @PostMapping("/read")
    public Result<Void> markRead(@CurrentUser LoginUser user, @RequestBody ReadDTO dto) {
        notificationService.markRead(user.getUserId(), dto.getId());
        return Result.ok();
    }

    /** 全部标记已读。 */
    @PostMapping("/read-all")
    public Result<Integer> markAllRead(@CurrentUser LoginUser user) {
        return Result.ok(notificationService.markAllRead(user.getUserId()));
    }

    /** 已读入参。 */
    @Data
    public static class ReadDTO {
        private Long id;
    }
}
