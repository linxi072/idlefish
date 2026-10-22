package com.idlefish.trade.search.serving;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隔离线程池单测（ITER-T04 / REQ-09）：超时取消、饱和快速失败、fire-and-forget 不抛错、快照字段。
 */
class SearchServiceExecutorTest {

    @Test
    @DisplayName("正常执行：返回值透传")
    void callSuccess() {
        SearchServiceExecutor ex = new SearchServiceExecutor(2, 4, 16, 1000L);
        try {
            assertEquals("ok", ex.call(() -> "ok"));
        } finally {
            ex.shutdown();
        }
    }

    @Test
    @DisplayName("任务超时：主动取消并抛 SearchServingException(executor_timeout)")
    void callTimeout() {
        SearchServiceExecutor ex = new SearchServiceExecutor(2, 4, 16, 200L);
        try {
            SearchServingException e = assertThrows(SearchServingException.class, () -> ex.call(() -> {
                Thread.sleep(3000L);
                return "slow";
            }));
            assertEquals("executor_timeout", e.getReason());
            assertEquals(1L, ((Number) ex.stats().get("timeouts")).longValue());
        } finally {
            ex.shutdown();
        }
    }

    @Test
    @DisplayName("池饱和（线程+队列占满）：快速失败抛 executor_unavailable，不阻塞调用线程")
    void saturationFailsFast() throws Exception {
        SearchServiceExecutor ex = new SearchServiceExecutor(1, 1, 1, 2000L);
        try {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            // 占满唯一工作线程
            ex.runQuietly(() -> {
                entered.countDown();
                await(release);
            });
            assertTrue(entered.await(1, TimeUnit.SECONDS), "工作线程应被占满");
            // 占满唯一队列格
            ex.runQuietly(() -> {
            });
            // 第三次提交：必须被拒绝
            SearchServingException e = assertThrows(SearchServingException.class,
                    () -> ex.call(() -> "rejected"));
            assertEquals("executor_unavailable", e.getReason());
            assertTrue(((Number) ex.stats().get("rejected")).longValue() >= 1);
            release.countDown();
        } finally {
            ex.shutdown();
        }
    }

    @Test
    @DisplayName("fire-and-forget：池饱和时静默丢弃，不抛给调用方")
    void runQuietlyNeverThrows() throws Exception {
        SearchServiceExecutor ex = new SearchServiceExecutor(1, 1, 1, 500L);
        try {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ex.runQuietly(() -> {
                entered.countDown();
                await(release);
            });
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            // 连续灌入远超容量的任务：必须静默吞掉
            for (int i = 0; i < 50; i++) {
                ex.runQuietly(() -> {
                });
            }
            release.countDown();
            Map<String, Object> stats = ex.stats();
            assertNotNull(stats);
            assertTrue(((Number) stats.get("rejected")).longValue() >= 1);
        } finally {
            ex.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
