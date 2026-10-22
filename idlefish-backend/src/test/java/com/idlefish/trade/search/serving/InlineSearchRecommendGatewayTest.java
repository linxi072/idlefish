package com.idlefish.trade.search.serving;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.enhance.service.SearchEnhanceService;
import com.idlefish.trade.search.service.RecommendService;
import com.idlefish.trade.search.serving.dto.ItemQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内联适配器单测（ITER-T04 / REQ-10）：委托既有服务 + 异常包装为 SearchServingException。
 */
class InlineSearchRecommendGatewayTest {

    private SearchEnhanceService enhanceService;
    private RecommendService recommendService;
    private InlineSearchRecommendGateway gateway;

    @BeforeEach
    void setUp() {
        enhanceService = mock(SearchEnhanceService.class);
        recommendService = mock(RecommendService.class);
        gateway = new InlineSearchRecommendGateway(enhanceService, recommendService);
    }

    @Test
    @DisplayName("模式标识为 inline")
    void mode() {
        assertEquals("inline", gateway.mode());
    }

    @Test
    @DisplayName("search/guess/feed/behavior 原样委托既有服务")
    void delegates() {
        ItemQuery q = new ItemQuery();
        q.setKeyword("手机");
        Page<ItemVO> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(new ItemVO()));
        when(enhanceService.enhancedSearch(any(), eq(1001L))).thenReturn(page);
        when(enhanceService.guessYouWant(eq("手机"), eq(10))).thenReturn(List.of(new ItemVO()));
        when(recommendService.feed(eq("北京"), eq(1001L), eq(1), eq(10))).thenReturn(page);

        IPage<ItemVO> r1 = gateway.search(q, 1001L);
        assertSame(page, r1);
        List<ItemVO> r2 = gateway.guess("手机", 10);
        assertEquals(1, r2.size());
        IPage<ItemVO> r3 = gateway.feed("北京", 1001L, 1, 10);
        assertSame(page, r3);

        gateway.recordBehavior(1001L, "view_item", "88");
        verify(enhanceService).recordBehavior(1001L, "view_item", "88");
    }

    @Test
    @DisplayName("既有服务异常：包装为 SearchServingException(inline_error)，不裸抛")
    void wrapsException() {
        when(enhanceService.enhancedSearch(any(), any())).thenThrow(new IllegalStateException("boom"));
        SearchServingException e = assertThrows(SearchServingException.class,
                () -> gateway.search(new ItemQuery(), 1L));
        assertEquals("inline_error", e.getReason());

        when(enhanceService.guessYouWant(any(), anyInt())).thenThrow(new IllegalStateException("boom"));
        assertThrows(SearchServingException.class, () -> gateway.guess("x", 10));

        when(recommendService.feed(any(), any(), anyInt(), anyInt())).thenThrow(new IllegalStateException("boom"));
        assertThrows(SearchServingException.class, () -> gateway.feed(null, null, 1, 10));

        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(enhanceService).recordBehavior(any(), any(), any());
        assertThrows(SearchServingException.class,
                () -> gateway.recordBehavior(1L, "view_item", "1"));
    }
}
