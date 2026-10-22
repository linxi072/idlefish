package com.idlefish.trade.search.serving;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.serving.dto.ItemQuery;
import com.idlefish.trade.search.serving.dto.ServingOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 路由门面单测（ITER-T04 / REQ-03/08/09/10）：模式路由、降级阶梯、熔断跳过、隔离池饱和降级、
 * 行为回传绝不抛错。
 */
class SearchServingFacadeTest {

    private IdlefishProperties props;
    private InlineSearchRecommendGateway inline;
    private HttpSearchRecommendGateway remote;
    private CircuitBreaker breaker;
    private SearchServiceExecutor executor;
    private SyncLagTracker lagTracker;
    private SearchServingFacade facade;

    private static ItemQuery query() {
        ItemQuery q = new ItemQuery();
        q.setKeyword("手机");
        q.setPage(1);
        q.setSize(20);
        return q;
    }

    private static Page<ItemVO> pageOf(int n) {
        Page<ItemVO> p = new Page<>(1, 20, n);
        p.setRecords(n == 0 ? List.of() : List.of(new ItemVO()));
        return p;
    }

    private void build(IdlefishProperties.Mode mode) {
        props = new IdlefishProperties();
        IdlefishProperties.SearchService cfg = new IdlefishProperties.SearchService();
        cfg.setMode(mode);
        props.setSearchService(cfg);
        inline = mock(InlineSearchRecommendGateway.class);
        remote = mock(HttpSearchRecommendGateway.class);
        breaker = new CircuitBreaker(2, 10_000L);
        executor = new SearchServiceExecutor(4, 8, 32, 2000L);
        lagTracker = new SyncLagTracker();
        facade = new SearchServingFacade(props, inline, remote, breaker, executor, lagTracker,
                new MetricsRegistry());
    }

    @BeforeEach
    void setUp() {
        build(IdlefishProperties.Mode.INLINE);
    }

    @Test
    @DisplayName("内联模式：直接走内联适配器，不降级")
    void inlineMode() {
        when(inline.search(any(), any())).thenReturn(pageOf(1));
        ServingOutcome<IPage<ItemVO>> out = facade.search(query(), 1001L);
        assertFalse(out.isDegraded());
        assertEquals("inline", out.getMode());
        assertEquals(1, out.getData().getTotal());
        verify(remote, never()).search(any(), any());
    }

    @Test
    @DisplayName("远程模式成功：走远程且不降级")
    void remoteModeSuccess() {
        build(IdlefishProperties.Mode.REMOTE);
        when(remote.search(any(), any())).thenReturn(pageOf(2));
        ServingOutcome<IPage<ItemVO>> out = facade.search(query(), 1001L);
        assertFalse(out.isDegraded());
        assertEquals("remote", out.getMode());
        assertEquals(2, out.getData().getTotal());
        verify(inline, never()).search(any(), any());
    }

    @Test
    @DisplayName("远程失败：回落内联并标注降级 remote_failed")
    void remoteFailsFallbackInline() {
        build(IdlefishProperties.Mode.REMOTE);
        when(remote.search(any(), any())).thenThrow(new SearchServingException("remote_timeout", "timeout"));
        when(inline.search(any(), any())).thenReturn(pageOf(1));
        ServingOutcome<IPage<ItemVO>> out = facade.search(query(), 1001L);
        assertTrue(out.isDegraded());
        assertEquals("remote_failed", out.getReason());
        assertEquals("inline", out.getMode());
        assertEquals(1, out.getData().getTotal());
    }

    @Test
    @DisplayName("熔断打开：跳过远程直接回落内联，标注 breaker_open")
    void breakerOpenSkipsRemote() {
        build(IdlefishProperties.Mode.REMOTE);
        when(remote.search(any(), any())).thenThrow(new SearchServingException("remote_timeout", "timeout"));
        when(inline.search(any(), any())).thenReturn(pageOf(1));
        facade.search(query(), 1L); // 失败 1
        facade.search(query(), 1L); // 失败 2 → 熔断
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        ServingOutcome<IPage<ItemVO>> out = facade.search(query(), 1L); // 第 3 次：熔断中
        assertTrue(out.isDegraded());
        assertEquals("breaker_open", out.getReason());
        verify(remote, times(2)).search(any(), any());
    }

    @Test
    @DisplayName("内联也失败：走热门兜底（guess），标注 hot_fallback")
    void inlineFailsHotFallback() {
        when(inline.search(any(), any())).thenThrow(new SearchServingException("inline_error", "boom"));
        when(inline.guess(anyString(), anyInt())).thenReturn(List.of(new ItemVO()));
        ServingOutcome<IPage<ItemVO>> out = facade.search(query(), 1L);
        assertTrue(out.isDegraded());
        assertTrue(out.getReason().contains("hot_fallback"));
        assertEquals(1, out.getData().getRecords().size());
    }

    @Test
    @DisplayName("全部失败：返回空页且绝不抛错")
    void allFailEmptyPage() {
        when(inline.search(any(), any())).thenThrow(new SearchServingException("inline_error", "boom"));
        when(inline.guess(anyString(), anyInt())).thenThrow(new SearchServingException("inline_error", "boom"));
        ServingOutcome<IPage<ItemVO>> out = facade.search(query(), 1L);
        assertTrue(out.isDegraded());
        assertTrue(out.getReason().contains("all_failed"));
        assertEquals(0, out.getData().getTotal());
    }

    @Test
    @DisplayName("兜底召回：远程失败回落内联")
    void guessFallback() {
        build(IdlefishProperties.Mode.REMOTE);
        when(remote.guess(anyString(), anyInt())).thenThrow(new SearchServingException("remote_error", "boom"));
        when(inline.guess(anyString(), anyInt())).thenReturn(List.of(new ItemVO(), new ItemVO()));
        ServingOutcome<List<ItemVO>> out = facade.guess("手机", 10);
        assertTrue(out.isDegraded());
        assertEquals(2, out.getData().size());
    }

    @Test
    @DisplayName("推荐流：远程失败回落内联")
    void feedFallback() {
        build(IdlefishProperties.Mode.REMOTE);
        when(remote.feed(any(), any(), anyInt(), anyInt())).thenThrow(new SearchServingException("remote_error", "boom"));
        when(inline.feed(any(), any(), anyInt(), anyInt())).thenReturn(pageOf(3));
        ServingOutcome<IPage<ItemVO>> out = facade.feed("北京", 1001L, 1, 10);
        assertTrue(out.isDegraded());
        assertEquals("remote_failed", out.getReason());
        assertEquals(3, out.getData().getTotal());
    }

    @Test
    @DisplayName("数据时效标注：滞后跟踪器记录后透出秒数")
    void dataAgeAnnotation() {
        when(inline.search(any(), any())).thenReturn(pageOf(1));
        lagTracker.recordSuccessAt(SyncLagTracker.ENTITY_ITEM, System.currentTimeMillis() - 30_000L);
        ServingOutcome<IPage<ItemVO>> out = facade.search(query(), 1L);
        long age = out.getDataAgeSeconds();
        assertTrue(age >= 29 && age <= 31, "数据时效应在 29~31s，实际 " + age);
    }

    @Test
    @DisplayName("行为回传：内联模式委托，绝不抛错")
    void recordBehaviorNeverThrows() {
        facade.recordBehavior(1001L, "view_item", "88");
        // fire-and-forget：给隔离池执行时间后验证
        long deadline = System.currentTimeMillis() + 2000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(inline, times(1)).recordBehavior(1001L, "view_item", "88");
                return;
            } catch (AssertionError retry) {
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        verify(inline, times(1)).recordBehavior(1001L, "view_item", "88");
    }

    @Test
    @DisplayName("行为回传：远程异常被吞掉，不影响调用方")
    void recordBehaviorSwallowsRemoteError() throws Exception {
        build(IdlefishProperties.Mode.REMOTE);
        org.mockito.Mockito.doThrow(new SearchServingException("remote_error", "boom"))
                .when(remote).recordBehavior(any(), any(), any());
        facade.recordBehavior(1001L, "view_item", "88"); // 不得抛错
        Thread.sleep(300L);
        verify(remote, times(1)).recordBehavior(1001L, "view_item", "88");
    }

    @Test
    @DisplayName("隔离池饱和：返回空页降级 executor_unavailable，不上抛")
    void executorSaturationDegrades() throws Exception {
        SearchServiceExecutor tiny = new SearchServiceExecutor(1, 1, 1, 2000L);
        SearchServingFacade f = new SearchServingFacade(props, inline, remote, breaker, tiny, lagTracker,
                new MetricsRegistry());
        when(inline.search(any(), any())).thenReturn(pageOf(1));
        try {
            java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            tiny.runQuietly(() -> {
                entered.countDown();
                try {
                    release.await(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS));
            tiny.runQuietly(() -> {
            }); // 占满队列
            ServingOutcome<IPage<ItemVO>> out = f.search(query(), 1L);
            assertTrue(out.isDegraded());
            assertEquals("executor_unavailable", out.getReason());
            assertNotNull(out.getData());
            assertEquals(0, out.getData().getTotal());
            release.countDown();
        } finally {
            tiny.shutdown();
        }
    }
}
