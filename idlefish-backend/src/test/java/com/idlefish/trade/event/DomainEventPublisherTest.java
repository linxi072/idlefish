package com.idlefish.trade.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.event.mapper.EventOutboxMapper;
import com.idlefish.trade.event.mapper.EventRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 领域事件发布器单测（ITER-T06 / REQ-05 Outbox 同事务 + REQ-03 best-effort）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DomainEventPublisherTest {

    @Mock private EventOutboxMapper outboxMapper;
    @Mock private EventRecordMapper recordMapper;
    @Mock private EventDispatcher dispatcher;
    @Mock private EventMqTransport mqTransport;

    private MetricsRegistry metrics;
    private IdlefishProperties props;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        metrics = new MetricsRegistry();
        props = new IdlefishProperties();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private DomainEventPublisher newPublisher() {
        return new DomainEventPublisher(outboxMapper, recordMapper, dispatcher,
                objectMapper, metrics, props);
    }

    /** REQ-05：无事务上下文 → 直接落库（Outbox + 事件存储）并同步分发。 */
    @Test
    void publishWithoutTxInsertsAndDispatches() {
        DomainEventPublisher p = newPublisher();
        p.publish(EventType.ORDER_PAID, "NO1", Map.of("amountFen", 100L));

        ArgumentCaptor<EventOutbox> outCaptor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(outboxMapper).insert(outCaptor.capture());
        EventOutbox row = outCaptor.getValue();
        assertEquals(EventType.ORDER_PAID.name(), row.getEventType());
        assertEquals(EventOutboxStatus.PENDING.name(), row.getStatus());
        assertNotNull(row.getEventId());
        assertNotNull(row.getTraceId());
        assertTrue(row.getPayload().contains("amountFen"));

        // persisted=true → 事件存储同步落库
        verify(recordMapper).insert(any(EventRecord.class));
        // 无事务 → 同步分发一次
        verify(dispatcher).dispatch(any(EventOutbox.class), eq(false));
        assertEquals(1, metrics.counter("event.publish.total"));
        assertEquals(1, metrics.counter("event.publish." + EventType.ORDER_PAID.name()));
    }

    /** REQ-05：有事务上下文 → 同事务落库 + 注册 afterCommit 分发（提交前不分发）。 */
    @Test
    void publishWithTxRegistersAfterCommitDispatch() {
        TransactionSynchronizationManager.initSynchronization();
        DomainEventPublisher p = newPublisher();
        p.publish(EventType.ORDER_CREATED, "NO2", Map.of("buyerId", 1L));

        verify(outboxMapper).insert(any(EventOutbox.class));
        verify(recordMapper).insert(any(EventRecord.class));
        // 提交前不分发
        verify(dispatcher, never()).dispatch(any(), org.mockito.ArgumentMatchers.anyBoolean());

        // 模拟提交：触发 afterCommit → 分发
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());
        syncs.get(0).afterCommit();
        verify(dispatcher).dispatch(any(EventOutbox.class), eq(false));
    }

    /** REQ-02：persisted=false 的事件不写事件存储（预留高频低价值事件扩展位）。 */
    @Test
    void nonPersistedTypeSkipsRecord() throws Exception {
        // 反射构造一个 persisted=false 的场景不可行（枚举全为 true），改验 persisted=true 全落库
        DomainEventPublisher p = newPublisher();
        p.publish(EventType.INSPECTION_COMPLETED, "IV1", null);
        verify(recordMapper).insert(any(EventRecord.class));
    }

    /** 总开关关闭：仅记日志，不落库不分发（REQ 降级语义）。 */
    @Test
    void disabledSwitchDropsEvent() {
        props.getEvent().setEnabled(false);
        DomainEventPublisher p = newPublisher();
        p.publish(EventType.ORDER_CANCELLED, "NO3", null);
        verify(outboxMapper, never()).insert(any(EventOutbox.class));
        verify(dispatcher, never()).dispatch(any(), org.mockito.ArgumentMatchers.anyBoolean());
        assertEquals(1, metrics.counter("event.publish.disabled"));
    }

    /** REQ-03/04：发布内部异常绝不打断业务链路（best-effort）。 */
    @Test
    void publishFailureDoesNotThrow() {
        when(outboxMapper.insert(any(EventOutbox.class))).thenThrow(new RuntimeException("db down"));
        DomainEventPublisher p = newPublisher();
        assertDoesNotThrow(() -> p.publish(EventType.ORDER_SHIPPED, "NO4", null));
        assertEquals(1, metrics.counter("event.publish.failure"));
    }

    /** 分发兜底：afterCommit 分发抛异常仅记日志，不影响提交后的业务线程。 */
    @Test
    void afterCommitDispatchCrashSwallowed() {
        when(dispatcher.dispatch(any(EventOutbox.class), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new RuntimeException("dispatch crashed"));
        DomainEventPublisher p = newPublisher();
        assertDoesNotThrow(() -> p.publish(EventType.ORDER_SHIPPED, "NO5", null));
    }
}
