package com.idlefish.trade.search.serving;

import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.search.service.SearchService;
import com.idlefish.trade.search.serving.dto.ItemSyncRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 索引同步编排单测（ITER-T04 / REQ-02/07）：消息投递 + 本地双写 + 滞后记录 + 异常不阻断。
 */
class SearchSyncServiceTest {

    private SearchSyncProducer producer;
    private SearchService searchService;
    private ItemMapper itemMapper;
    private SyncLagTracker lagTracker;
    private SearchSyncService service;

    @BeforeEach
    void setUp() {
        producer = mock(SearchSyncProducer.class);
        searchService = mock(SearchService.class);
        itemMapper = mock(ItemMapper.class);
        lagTracker = new SyncLagTracker();
        service = new SearchSyncService(producer, searchService, itemMapper, lagTracker, new MetricsRegistry());
    }

    @Test
    @DisplayName("UPSERT：投递消息 + 本地索引写入 + 滞后基线清零")
    void upsertWritesBoth() {
        Item item = new Item();
        item.setId(88L);
        when(itemMapper.selectById(88L)).thenReturn(item);

        service.onItemChanged(88L, SearchSyncService.EVENT_UPSERT);

        verify(producer, times(1)).publishItemEvent(88L, SearchSyncService.EVENT_UPSERT);
        verify(searchService, times(1)).indexItem(item);
        verify(searchService, never()).removeItem(anyLong());
        assertEquals(0L, lagTracker.failures(SyncLagTracker.ENTITY_ITEM));
    }

    @Test
    @DisplayName("REMOVE：投递消息 + 索引移除")
    void removeEvent() {
        service.onItemChanged(88L, SearchSyncService.EVENT_REMOVE);
        verify(producer, times(1)).publishItemEvent(88L, SearchSyncService.EVENT_REMOVE);
        verify(searchService, times(1)).removeItem(88L);
        verify(itemMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("商品已不存在：按移除处理，避免脏文档")
    void upsertMissingItemRemoves() {
        when(itemMapper.selectById(88L)).thenReturn(null);
        service.onItemChanged(88L, SearchSyncService.EVENT_UPSERT);
        verify(searchService, times(1)).removeItem(88L);
    }

    @Test
    @DisplayName("索引写入失败：记滞后失败但不抛异常（不阻断商品主流程）")
    void indexFailureDoesNotThrow() {
        when(itemMapper.selectById(88L)).thenReturn(new Item());
        doThrow(new RuntimeException("es down")).when(searchService).indexItem(any());

        service.onItemChanged(88L, SearchSyncService.EVENT_UPSERT); // 不得抛错

        assertEquals(1L, lagTracker.failures(SyncLagTracker.ENTITY_ITEM));
    }

    @Test
    @DisplayName("消息投递异常被吞掉（best-effort）")
    void producerFailureDoesNotThrow() {
        doThrow(new RuntimeException("mq down")).when(producer).publishItemEvent(any(), any());
        when(itemMapper.selectById(88L)).thenReturn(new Item());
        service.onItemChanged(88L, SearchSyncService.EVENT_UPSERT);
        verify(searchService, times(1)).indexItem(any());
    }

    @Test
    @DisplayName("消费侧回调：按事件时间回填滞后口径；失败上抛供消费重试")
    void applySyncedItem() {
        Item item = new Item();
        item.setId(88L);
        when(itemMapper.selectById(88L)).thenReturn(item);
        long occurredAt = System.currentTimeMillis() - 15_000L;
        service.applySyncedItem(new ItemSyncRequest(88L, SearchSyncService.EVENT_UPSERT, occurredAt));
        verify(searchService, times(1)).indexItem(item);
        long lag = lagTracker.lagSeconds(SyncLagTracker.ENTITY_ITEM);
        assertTrue(lag >= 14 && lag <= 16, "滞后应按 occurredAt 回填，实际 " + lag);

        doThrow(new RuntimeException("es down")).when(searchService).indexItem(any());
        assertThrows(RuntimeException.class,
                () -> service.applySyncedItem(new ItemSyncRequest(88L, SearchSyncService.EVENT_UPSERT, null)));
        assertEquals(1L, lagTracker.failures(SyncLagTracker.ENTITY_ITEM));
    }
}
