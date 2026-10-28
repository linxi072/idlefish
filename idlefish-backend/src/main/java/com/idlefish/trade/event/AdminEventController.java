package com.idlefish.trade.event;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 领域事件治理后台（ITER-T06 / PRD-P2-T06）。
 * 六个端点均 auth=admin（RBAC 拦截器统一鉴权），覆盖：
 * 事件类型注册表查询（REQ-01/02）、Outbox 概览（REQ-06/08）、事件存储检索（REQ-09）、
 * 限流回放创建与进度（REQ-13）、死信重投（REQ-08）。
 */
@RestController
@RequestMapping("/api/admin/event")
public class AdminEventController {

    private final EventReplayService replayService;

    public AdminEventController(EventReplayService replayService) {
        this.replayService = replayService;
    }

    /** 事件类型注册表：code / 说明 / schema 版本 / 是否持久化（REQ-01/02）。 */
    @GetMapping("/types")
    public Result<List<TypeInfo>> types(@CurrentAdmin AdminUser admin) {
        return Result.ok(TypeInfo.all());
    }

    /** Outbox 概览：各状态计数 + 死信存量（REQ-06/08）。 */
    @GetMapping("/outbox/stats")
    public Result<Map<String, Object>> outboxStats(@CurrentAdmin AdminUser admin) {
        return Result.ok(replayService.outboxStats());
    }

    /** 事件存储分页检索（类型 / 聚合根 / 时间窗，REQ-09）。 */
    @GetMapping("/list")
    public Result<Page<EventRecord>> list(@CurrentAdmin AdminUser admin,
                                          @RequestParam(required = false) String eventType,
                                          @RequestParam(required = false) String aggregateId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime fromTime,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime toTime,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(replayService.listEvents(eventType, aggregateId, fromTime, toTime, page, size));
    }

    /** 创建回放任务（限流执行，副作用监听器自动跳过，REQ-13）。 */
    @PostMapping("/replay")
    public Result<EventReplayTask> replay(@CurrentAdmin AdminUser admin,
                                          @Valid @RequestBody ReplayRequest req) {
        return Result.ok(replayService.createReplay(admin.getId(), req.getEventType(),
                req.getFromTime(), req.getToTime()));
    }

    /** 回放任务列表（最新在前，前端轮询进度）。 */
    @GetMapping("/replay/list")
    public Result<Page<EventReplayTask>> replayList(@CurrentAdmin AdminUser admin,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.ok(replayService.listReplays(page, size));
    }

    /** 死信重投（REQ-08）：eventId 为空则重投全部死信；无可重投返回 EVENT_DEAD_LETTER_EMPTY。 */
    @PostMapping("/dead-letter/redrive")
    public Result<Map<String, Object>> redrive(@CurrentAdmin AdminUser admin,
                                               @RequestBody(required = false) RedriveRequest req) {
        String eventId = req == null ? null : req.getEventId();
        int count = replayService.redriveDeadLetters(admin.getId(), eventId);
        return Result.ok(Map.of("redriven", count));
    }

    // ===== 请求 / 视图模型 =====

    /** 回放创建请求。 */
    @Data
    public static class ReplayRequest {
        @NotBlank(message = "回放事件类型不可为空")
        private String eventType;
        @NotNull(message = "回放起始时间不可为空")
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime fromTime;
        @NotNull(message = "回放结束时间不可为空")
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime toTime;
    }

    /** 死信重投请求（eventId 可空 = 全量重投）。 */
    @Data
    public static class RedriveRequest {
        private String eventId;
    }

    /** 事件类型视图。 */
    @Data
    public static class TypeInfo {
        private String code;
        private String desc;
        private int version;
        private boolean persisted;

        static List<TypeInfo> all() {
            return java.util.Arrays.stream(EventType.values()).map(t -> {
                TypeInfo i = new TypeInfo();
                i.setCode(t.name());
                i.setDesc(t.getDesc());
                i.setVersion(t.getVersion());
                i.setPersisted(t.isPersisted());
                return i;
            }).toList();
        }
    }
}
