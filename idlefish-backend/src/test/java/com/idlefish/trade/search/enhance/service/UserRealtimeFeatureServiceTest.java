package com.idlefish.trade.search.enhance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.cache.CacheService;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户实时兴趣/意图特征单测（ITER-F04 / REQ-01 60s 内更新、REQ-09 反哺推荐）。
 * Mock 缓存与 Mapper，使用真实 ObjectMapper（JSON 序列化无需 mock），全程 offline。
 */
class UserRealtimeFeatureServiceTest {

    private final CacheService cache = mock(CacheService.class);
    private final ItemMapper itemMapper = mock(ItemMapper.class);
    private final MetricsRegistry metrics = mock(MetricsRegistry.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRealtimeFeatureService service =
            new UserRealtimeFeatureService(cache, objectMapper, itemMapper, metrics);

    @Test
    @DisplayName("搜索行为记录意图（search 事件写入意图词，60s 窗口）")
    void recordSearchIntent() {
        service.recordBehavior(1001L, "search", "iPhone");
        verify(cache, times(1)).put(eq("rt:intent:1001"), any(), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("商品行为记录类目兴趣（view_item 累加权重，查 Item 取类目）")
    void recordItemInterest() {
        Item item = new Item();
        item.setId(55L);
        item.setCategoryId(7L);
        when(itemMapper.selectById(55L)).thenReturn(item);

        service.recordBehavior(1002L, "view_item", "55");
        verify(cache, times(1)).put(eq("rt:interest:1002"), any(), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("读取实时兴趣：缓存命中返回解析后的权重表")
    void getRealtimeInterest() throws Exception {
        Map<Long, Double> weights = Map.of(7L, 1.0, 9L, 3.0);
        when(cache.get("rt:interest:1003")).thenReturn(objectMapper.writeValueAsString(weights));
        Map<Long, Double> got = service.getRealtimeInterest(1003L);
        assertEquals(1.0, got.get(7L));
        assertEquals(3.0, got.get(9L));
    }

    @Test
    @DisplayName("读取最近意图：最新在前")
    void getRecentIntents() throws Exception {
        List<String> intents = List.of("iPhone", "华为", "小米");
        when(cache.get("rt:intent:1004")).thenReturn(objectMapper.writeValueAsString(intents));
        List<String> got = service.getRecentIntents(1004L);
        assertEquals("iPhone", got.get(0));
        assertEquals(3, got.size());
    }

    @Test
    @DisplayName("null 用户：不记录、读取返回空（best-effort 安全）")
    void nullUserSafe() {
        service.recordBehavior(null, "view_item", "55");
        assertTrue(service.getRealtimeInterest(null).isEmpty());
        assertTrue(service.getRecentIntents(null).isEmpty());
    }

    @Test
    @DisplayName("缓存异常：读取降级返回空，不抛异常")
    void cacheErrorDegrade() {
        when(cache.get("rt:interest:1005")).thenThrow(new RuntimeException("redis down"));
        assertTrue(service.getRealtimeInterest(1005L).isEmpty());
    }

    @Test
    @DisplayName("事件权重：收藏>支付>下单>浏览，未知事件 0")
    void eventWeight() {
        assertEquals(1.0, UserRealtimeFeatureService.eventWeight("view_item"));
        assertEquals(3.0, UserRealtimeFeatureService.eventWeight("favorite"));
        assertEquals(5.0, UserRealtimeFeatureService.eventWeight("order_create"));
        assertEquals(4.0, UserRealtimeFeatureService.eventWeight("pay"));
        assertEquals(0.0, UserRealtimeFeatureService.eventWeight("unknown"));
    }
}
