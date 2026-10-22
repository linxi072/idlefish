package com.idlefish.trade.search.serving;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 搜推专用有界线程池（ITER-T04 / REQ-09）：检索/召回/融合排序一律在此执行，
 * 与交易主链路（Tomcat 请求线程 / 支付回调）资源隔离，峰值互不拖累。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>有界队列 + AbortPolicy：池饱和时快速失败，由上层降级（返回兜底/空结果），绝不阻塞请求线程；</li>
 *   <li>调用带超时（{@code timeoutMs}）：超时主动 cancel，避免下游拖死池线程；</li>
 *   <li>守护线程 + 固定命名前缀，便于线程转储定位；进程退出时优雅停机；</li>
 *   <li>拒绝/超时/异常统一包装为 {@link SearchServingException}，上层无感差异。</li>
 * </ul>
 */
public class SearchServiceExecutor {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceExecutor.class);

    private final ThreadPoolExecutor executor;
    private final long timeoutMs;
    private final AtomicLong rejected = new AtomicLong(0);
    private final AtomicLong timeouts = new AtomicLong(0);

    public SearchServiceExecutor(int corePoolSize, int maxPoolSize, int queueCapacity, long timeoutMs) {
        this.timeoutMs = Math.max(200L, timeoutMs);
        this.executor = new ThreadPoolExecutor(
                Math.max(1, corePoolSize),
                Math.max(1, maxPoolSize),
                60L, TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(Math.max(1, queueCapacity)),
                runnable -> {
                    Thread t = new Thread(runnable, "search-serving-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                },
                // 饱和即拒绝（降级由上层处理），绝不用 CallerRuns 把负载倒灌回请求线程
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 在隔离线程池中执行任务并等待结果。
     *
     * @param task 检索/推荐任务（内联或远程调用）
     * @return 任务结果
     * @throws SearchServingException 池饱和 / 超时 / 任务异常（统一包装，上层降级）
     */
    public <T> T call(Callable<T> task) {
        Future<T> future;
        try {
            future = executor.submit(task);
        } catch (RejectedExecutionException e) {
            rejected.incrementAndGet();
            throw new SearchServingException("executor_unavailable", "搜推隔离线程池已饱和，快速失败降级", e);
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            timeouts.incrementAndGet();
            throw new SearchServingException("executor_interrupted", "搜推任务被中断", e);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            timeouts.incrementAndGet();
            throw new SearchServingException("executor_timeout",
                    "搜推任务超时（>" + timeoutMs + "ms），主动取消降级", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new SearchServingException("task_failed", "搜推任务执行失败: " + cause.getMessage(), cause);
        }
    }

    /**  fire-and-forget：行为回传等非关键路径，池饱和时静默丢弃（计数 + 日志），绝不抛给调用方。 */
    public void runQuietly(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            rejected.incrementAndGet();
            log.warn("[search:serving] 隔离线程池饱和，丢弃非关键任务（behavior）");
        }
    }

    /** 线程池快照（供状态端点与单测）。 */
    public java.util.Map<String, Object> stats() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("activeCount", executor.getActiveCount());
        m.put("poolSize", executor.getPoolSize());
        m.put("corePoolSize", executor.getCorePoolSize());
        m.put("maximumPoolSize", executor.getMaximumPoolSize());
        m.put("queueSize", executor.getQueue().size());
        m.put("queueRemaining", executor.getQueue().remainingCapacity());
        m.put("completedTaskCount", executor.getCompletedTaskCount());
        m.put("rejected", rejected.get());
        m.put("timeouts", timeouts.get());
        return m;
    }

    /** 优雅停机（容器销毁时调用）。 */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
