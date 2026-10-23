package com.idlefish.trade.event;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.common.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminEventController 单测（ITER-T06）：6 端点委托 EventReplayService 并正确包装响应。
 * 纯 Mockito，离线可跑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminEventControllerTest {

    @Mock private EventReplayService replayService;
    @InjectMocks private AdminEventController controller;

    private AdminUser admin(long id) {
        AdminUser a = new AdminUser();
        a.setId(id);
        return a;
    }

    /** 类型注册表：返回全部注册类型（≥15，G1）。 */
    @Test
    void types_returnsAllRegistered() {
        Result<List<AdminEventController.TypeInfo>> r = controller.types(admin(1L));
        assertEquals(0, r.getCode());
        assertTrue(r.getData().size() >= 15);
        assertEquals(EventType.ORDER_PAID.name(),
                r.getData().stream().filter(t -> t.getCode().equals("ORDER_PAID")).findFirst().orElseThrow().getCode());
    }

    /** Outbox 概览：透传服务统计。 */
    @Test
    void outboxStats_delegates() {
        when(replayService.outboxStats()).thenReturn(Map.of("dead", 3L));
        Result<Map<String, Object>> r = controller.outboxStats(admin(1L));
        assertEquals(0, r.getCode());
        assertEquals(3L, r.getData().get("dead"));
    }

    /** 事件存储检索：过滤参数透传。 */
    @Test
    void list_delegatesWithFilters() {
        when(replayService.listEvents(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new Page<>(1, 20));
        Result<Page<EventRecord>> r = controller.list(admin(1L), "ORDER_PAID", "NO1", null, null, 1, 20);
        assertEquals(0, r.getCode());
        verify(replayService).listEvents(eq("ORDER_PAID"), eq("NO1"), isNull(), isNull(), eq(1), eq(20));
    }

    /** 回放创建：委托服务并返回任务。 */
    @Test
    void replay_delegates() {
        EventReplayTask task = new EventReplayTask();
        task.setReplayNo("EVR1");
        task.setEventType(EventType.ORDER_PAID.name());
        task.setStatus("PENDING");
        when(replayService.createReplay(anyLong(), any(), any(), any())).thenReturn(task);
        AdminEventController.ReplayRequest req = new AdminEventController.ReplayRequest();
        req.setEventType(EventType.ORDER_PAID.name());
        req.setFromTime(LocalDateTime.now().minusDays(1));
        req.setToTime(LocalDateTime.now());
        Result<EventReplayTask> r = controller.replay(admin(1L), req);
        assertEquals(0, r.getCode());
        assertEquals("EVR1", r.getData().getReplayNo());
    }

    /** 回放列表：分页透传。 */
    @Test
    void replayList_delegates() {
        when(replayService.listReplays(1, 20)).thenReturn(new Page<>(1, 20));
        Result<Page<EventReplayTask>> r = controller.replayList(admin(1L), 1, 20);
        assertEquals(0, r.getCode());
        verify(replayService).listReplays(1, 20);
    }

    /** 死信重投：eventId 为空全量重投，返回条数。 */
    @Test
    void redrive_delegatesAndReturnsCount() {
        when(replayService.redriveDeadLetters(anyLong(), isNull())).thenReturn(5);
        Result<Map<String, Object>> r = controller.redrive(admin(1L), new AdminEventController.RedriveRequest());
        assertEquals(0, r.getCode());
        assertEquals(5, r.getData().get("redriven"));
    }

    /** 死信重投：请求体为空（全量重投）不抛错。 */
    @Test
    void redrive_nullBodyTolerated() {
        when(replayService.redriveDeadLetters(anyLong(), isNull())).thenReturn(0);
        Result<Map<String, Object>> r = controller.redrive(admin(1L), null);
        assertEquals(0, r.getCode());
        assertEquals(0, r.getData().get("redriven"));
    }
}
