package com.idlefish.trade.event;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.event.mapper.EventOutboxMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 事件分发器单测（ITER-T06 / REQ-06 重试 / REQ-08 死信 / REQ-13 回放跳过副作用）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventDispatcherTest {

    @Mock private EventOutboxMapper outboxMapper;
    @Mock private EventMqTransport mqTransport;
    @Mock private DomainEventListener sideEffectListener;
    @Mock private DomainEventListener readOnlyListener;

    private MetricsRegistry metrics;
    private IdlefishProperties props;
    private EventDispatcher dispatcher;

    /** 无 Spring 上下文时初始化 MyBatis-Plus 实体元数据（lambda 缓存），否则 LambdaUpdateWrapper 解析失败。 */
    @BeforeAll
    static void initMpMetadata() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "dummy");
        TableInfoHelper.initTableInfo(assistant, EventOutbox.class);
    }

    @BeforeEach
    void setUp() {
        metrics = new MetricsRegistry();
        props = new IdlefishProperties();
        dispatcher = new EventDispatcher(List.of(sideEffectListener, readOnlyListener),
                outboxMapper, new ObjectMapper(), metrics, props, mqTransport);
        when(sideEffectListener.interests()).thenReturn(Set.of(EventType.ORDER_PAID));
        when(sideEffectListener.hasSideEffect()).thenReturn(true);
        when(readOnlyListener.interests()).thenReturn(Set.of(EventType.ORDER_PAID));
        when(readOnlyListener.hasSideEffect()).thenReturn(false);
    }

    private EventOutbox row(String eventType) {
        EventOutbox row = new EventOutbox();
        row.setId(1L);
        row.setEventId("e1");
        row.setEventType(eventType);
        row.setVersion(1);
        row.setAggregateId("NO1");
        row.setTraceId("t1");
        row.setPayload("{\"amountFen\":100}");
        row.setStatus(EventOutboxStatus.PENDING.name());
        row.setRetryCount(0);
        row.setNextRetryAt(LocalDateTime.now());
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    /** 正常分发：两类监听器（副作用 + 只读）均消费，Outbox 置 SENT。 */
    @Test
    void dispatchSuccessMarksSent() {
        EventDispatcher.DispatchResult r = dispatcher.dispatch(row(EventType.ORDER_PAID.name()), false);
        assertTrue(r.isOk());
        assertEquals(2, r.getMatched());
        verify(outboxMapper).update(eq(null), any());
        assertEquals(1, metrics.counter("event.dispatch.success"));
    }

    /** 消费失败 → FAILED + 退避重试（未耗尽时不转死信）。 */
    @Test
    void listenerFailureMarksFailedWithBackoff() {
        doThrow(new RuntimeException("boom")).when(sideEffectListener).onEvent(any());
        EventDispatcher.DispatchResult r = dispatcher.dispatch(row(EventType.ORDER_PAID.name()), false);
        assertFalse(r.isOk());
        assertEquals(1, metrics.counter("event.dispatch.failure"));
        ArgumentCaptor<Long> retry = ArgumentCaptor.forClass(Long.class);
        verify(outboxMapper).update(eq(null), any());
        // 行仍在 PENDING/FAILED 而非 DEAD：由 markFailed 更新（retryCount=1 < maxRetry=8）
        assertEquals(0, metrics.counter("event.dead.letter"));
    }

    /** 重试耗尽 → 死信（REQ-08）+ 计数。 */
    @Test
    void retryExhaustedMarksDead() {
        EventOutbox exhausted = row(EventType.ORDER_PAID.name());
        exhausted.setRetryCount(props.getEvent().getMaxRetry()); // 已达上限
        doThrow(new RuntimeException("boom")).when(sideEffectListener).onEvent(any());
        dispatcher.dispatch(exhausted, false);
        assertEquals(1, metrics.counter("event.dead.letter"));
    }

    /** 未知事件类型 → 直接死信（REQ-02 schema 演进安全）。 */
    @Test
    void unknownTypeMarksDead() {
        EventDispatcher.DispatchResult r = dispatcher.dispatch(row("LEGACY_TYPE"), false);
        assertFalse(r.isOk());
        assertEquals(1, metrics.counter("event.dead.letter"));
        verify(sideEffectListener, never()).onEvent(any());
    }

    /** REQ-13：回放流量跳过副作用监听器，只读监听器正常执行。 */
    @Test
    void replaySkipsSideEffectListener() {
        EventDispatcher.DispatchResult r = dispatcher.dispatch(row(EventType.ORDER_PAID.name()), true);
        assertTrue(r.isOk());
        verify(sideEffectListener, never()).onEvent(any());
        verify(readOnlyListener).onEvent(any());
        assertEquals(1, r.getSkipped());
        assertEquals(1, metrics.counter("event.dispatch.replaySkipped"));
    }

    /** 无监听器匹配：视为成功（SENT），MQ 镜像仍投递（非回放）。 */
    @Test
    void noMatchedListenerStillSent() {
        when(sideEffectListener.interests()).thenReturn(Set.of());
        when(readOnlyListener.interests()).thenReturn(Set.of());
        when(mqTransport.isAvailable()).thenReturn(true);
        EventDispatcher.DispatchResult r = dispatcher.dispatch(row(EventType.ORDER_PAID.name()), false);
        assertTrue(r.isOk());
        assertEquals(0, r.getMatched());
        verify(mqTransport).send(any(), any());
    }

    /** 指数退避：base*2^n 封顶。 */
    @Test
    void backoffDoublesAndCaps() {
        assertEquals(20, dispatcher.backoffSeconds(1));
        assertEquals(40, dispatcher.backoffSeconds(2));
        assertEquals(3600, dispatcher.backoffSeconds(30)); // 封顶 maxBackoffSeconds
    }
}
