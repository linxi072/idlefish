package com.idlefish.trade.search.serving;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.vo.ItemVO;
import com.idlefish.trade.search.serving.dto.ItemQuery;
import com.idlefish.trade.search.serving.dto.ServingOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 搜推服务化路由门面（ITER-T04 / REQ-03/08/09/10）：业务侧唯一入口。
 * <p>
 * 每次调用：
 * ① 在 {@link SearchServiceExecutor} 隔离线程池中执行（REQ-09，与交易主链路资源隔离）；
 * ② 按配置模式路由（REQ-10）：remote 先走 {@link HttpSearchRecommendGateway}，熔断器
 *    {@link CircuitBreaker} 关门才放行（REQ-08）；inline 直接走内联适配器；
 * ③ 失败按阶梯降级（REQ-03）：remote 失败 → 内联 → 热门兜底 → 空结果，
 *    任一级失败都返回可用结果并标注降级归因，绝不抛错阻断主流程；
 * ④ 附带数据时效（{@code SyncLagTracker} 滞后秒数，REQ-07 标注）与实际产出模式。
 */
@Service
public class SearchServingFacade {

    private static final Logger log = LoggerFactory.getLogger(SearchServingFacade.class);

    private final IdlefishProperties props;
    private final InlineSearchRecommendGateway inline;
    private final HttpSearchRecommendGateway remote;
    private final CircuitBreaker breaker;
    private final SearchServiceExecutor executor;
    private final SyncLagTracker lagTracker;
    private final MetricsRegistry metrics;

    public SearchServingFacade(IdlefishProperties props,
                               InlineSearchRecommendGateway inline,
                               HttpSearchRecommendGateway remote,
                               CircuitBreaker breaker,
                               SearchServiceExecutor executor,
                               SyncLagTracker lagTracker,
                               MetricsRegistry metrics) {
        this.props = props;
        this.inline = inline;
        this.remote = remote;
        this.breaker = breaker;
        this.executor = executor;
        this.lagTracker = lagTracker;
        this.metrics = metrics;
    }

    /** 路由结果（内部）：数据 + 是否降级 + 降级归因 + 实际产出模式。 */
    private record Routed<T>(T data, boolean degraded, String reason, String mode) {
        static <T> Routed<T> ok(T data, String mode) {
            return new Routed<>(data, false, null, mode);
        }
    }

    /** 当前路由模式（配置口径）：inline / remote。 */
    public String mode() {
        return isRemote() ? "remote" : "inline";
    }

    private boolean isRemote() {
        return props.getSearchService() != null
                && props.getSearchService().getMode() == IdlefishProperties.Mode.REMOTE;
    }

    private long dataAge() {
        long age = lagTracker.lagSeconds(SyncLagTracker.ENTITY_ITEM);
        return age < 0 ? -1L : age;
    }

    // ===== 增强检索 =====

    public ServingOutcome<IPage<ItemVO>> search(ItemQuery q, Long userId) {
        try {
            Routed<IPage<ItemVO>> r = executor.call(() -> routeSearch(q, userId));
            return r.degraded()
                    ? ServingOutcome.degraded(r.data(), dataAge(), r.reason(), r.mode())
                    : ServingOutcome.ok(r.data(), dataAge(), r.mode());
        } catch (SearchServingException e) {
            // 隔离池饱和 / 超时 / 任务异常：返回空页并标注，绝不上抛（REQ-03）
            metrics.increment("search.serving.search.degraded");
            log.warn("[search:serving] 检索降级 reason={} msg={}", e.getReason(), e.getMessage());
            return ServingOutcome.degraded(emptyPage(q), dataAge(), e.getReason(), mode());
        }
    }

    private Routed<IPage<ItemVO>> routeSearch(ItemQuery q, Long userId) {
        if (isRemote()) {
            if (!breaker.allowRequest()) {
                metrics.increment("search.serving.breaker.rejected");
                return inlineOrHotSearch(q, userId, "breaker_open");
            }
            try {
                IPage<ItemVO> r = remote.search(q, userId);
                breaker.recordSuccess();
                metrics.increment("search.serving.search.remote");
                return Routed.ok(r, "remote");
            } catch (RuntimeException e) {
                breaker.recordFailure();
                metrics.increment("search.serving.search.remote_failed");
                log.warn("[search:serving] 远程检索失败，回落内联: {}", e.getMessage());
                return inlineOrHotSearch(q, userId, "remote_failed");
            }
        }
        try {
            IPage<ItemVO> r = inline.search(q, userId);
            metrics.increment("search.serving.search.inline");
            return Routed.ok(r, "inline");
        } catch (RuntimeException e) {
            return inlineOrHotSearch(q, userId, "inline_error");
        }
    }

    /** 内联 → 热门兜底阶梯（降级路径专用）。 */
    private Routed<IPage<ItemVO>> inlineOrHotSearch(ItemQuery q, Long userId, String reason) {
        try {
            IPage<ItemVO> r = inline.search(q, userId);
            metrics.increment("search.serving.search.fallback_inline");
            return new Routed<>(r, true, reason, "inline");
        } catch (RuntimeException e) {
            metrics.increment("search.serving.search.inline_failed");
            log.warn("[search:serving] 内联检索失败，走热门兜底: {}", e.getMessage());
            try {
                List<ItemVO> hot = inline.guess(q.getKeyword(), pageSize(q));
                Page<ItemVO> p = new Page<>(page(q), pageSize(q), hot.size());
                p.setRecords(hot);
                metrics.increment("search.serving.search.fallback_hot");
                return new Routed<>(p, true, reason + "+hot_fallback", "inline");
            } catch (RuntimeException ex) {
                metrics.increment("search.serving.search.all_failed");
                return new Routed<>(emptyPage(q), true, reason + "+all_failed", "inline");
            }
        }
    }

    // ===== 兜底「猜你想找」 =====

    public ServingOutcome<List<ItemVO>> guess(String keyword, int limit) {
        try {
            Routed<List<ItemVO>> r = executor.call(() -> routeGuess(keyword, limit));
            return r.degraded()
                    ? ServingOutcome.degraded(r.data(), dataAge(), r.reason(), r.mode())
                    : ServingOutcome.ok(r.data(), dataAge(), r.mode());
        } catch (SearchServingException e) {
            metrics.increment("search.serving.guess.degraded");
            log.warn("[search:serving] 兜底召回降级 reason={} msg={}", e.getReason(), e.getMessage());
            return ServingOutcome.degraded(List.of(), dataAge(), e.getReason(), mode());
        }
    }

    private Routed<List<ItemVO>> routeGuess(String keyword, int limit) {
        if (isRemote()) {
            if (!breaker.allowRequest()) {
                metrics.increment("search.serving.breaker.rejected");
                return guessInline(keyword, limit, "breaker_open");
            }
            try {
                List<ItemVO> r = remote.guess(keyword, limit);
                breaker.recordSuccess();
                metrics.increment("search.serving.guess.remote");
                return Routed.ok(r, "remote");
            } catch (RuntimeException e) {
                breaker.recordFailure();
                metrics.increment("search.serving.guess.remote_failed");
                return guessInline(keyword, limit, "remote_failed");
            }
        }
        return guessInline(keyword, limit, null);
    }

    private Routed<List<ItemVO>> guessInline(String keyword, int limit, String reason) {
        try {
            List<ItemVO> r = inline.guess(keyword, limit);
            metrics.increment("search.serving.guess.inline");
            return reason == null ? Routed.ok(r, "inline") : new Routed<>(r, true, reason, "inline");
        } catch (RuntimeException e) {
            metrics.increment("search.serving.guess.inline_failed");
            return new Routed<>(List.of(), true,
                    (reason == null ? "inline_error" : reason) + "+empty", "inline");
        }
    }

    // ===== 推荐流 =====

    public ServingOutcome<IPage<ItemVO>> feed(String city, Long userId, int page, int size) {
        try {
            Routed<IPage<ItemVO>> r = executor.call(() -> routeFeed(city, userId, page, size));
            return r.degraded()
                    ? ServingOutcome.degraded(r.data(), dataAge(), r.reason(), r.mode())
                    : ServingOutcome.ok(r.data(), dataAge(), r.mode());
        } catch (SearchServingException e) {
            metrics.increment("search.serving.feed.degraded");
            log.warn("[search:serving] 推荐流降级 reason={} msg={}", e.getReason(), e.getMessage());
            Page<ItemVO> empty = new Page<>(Math.max(page, 1), Math.max(size, 1), 0);
            empty.setRecords(List.of());
            return ServingOutcome.degraded(empty, dataAge(), e.getReason(), mode());
        }
    }

    private Routed<IPage<ItemVO>> routeFeed(String city, Long userId, int page, int size) {
        if (isRemote()) {
            if (!breaker.allowRequest()) {
                metrics.increment("search.serving.breaker.rejected");
                return feedInline(city, userId, page, size, "breaker_open");
            }
            try {
                IPage<ItemVO> r = remote.feed(city, userId, page, size);
                breaker.recordSuccess();
                metrics.increment("search.serving.feed.remote");
                return Routed.ok(r, "remote");
            } catch (RuntimeException e) {
                breaker.recordFailure();
                metrics.increment("search.serving.feed.remote_failed");
                log.warn("[search:serving] 远程推荐失败，回落内联: {}", e.getMessage());
                return feedInline(city, userId, page, size, "remote_failed");
            }
        }
        return feedInline(city, userId, page, size, null);
    }

    private Routed<IPage<ItemVO>> feedInline(String city, Long userId, int page, int size, String reason) {
        try {
            IPage<ItemVO> r = inline.feed(city, userId, page, size);
            metrics.increment("search.serving.feed.inline");
            return reason == null ? Routed.ok(r, "inline") : new Routed<>(r, true, reason, "inline");
        } catch (RuntimeException e) {
            metrics.increment("search.serving.feed.inline_failed");
            Page<ItemVO> empty = new Page<>(Math.max(page, 1), Math.max(size, 1), 0);
            empty.setRecords(List.of());
            return new Routed<>(empty, true,
                    (reason == null ? "inline_error" : reason) + "+empty", "inline");
        }
    }

    // ===== 行为回传（非关键路径：隔离池 fire-and-forget，绝不抛错） =====

    public void recordBehavior(Long userId, String event, String bizId) {
        executor.runQuietly(() -> {
            try {
                if (isRemote() && breaker.allowRequest()) {
                    remote.recordBehavior(userId, event, bizId);
                    breaker.recordSuccess();
                    metrics.increment("search.serving.behavior.remote");
                } else {
                    inline.recordBehavior(userId, event, bizId);
                    metrics.increment("search.serving.behavior.inline");
                }
            } catch (RuntimeException e) {
                if (isRemote()) {
                    breaker.recordFailure();
                }
                metrics.increment("search.serving.behavior.error");
                log.warn("[search:serving] 行为回传失败（已丢弃，不影响主流程）: {}", e.getMessage());
            }
        });
    }

    // ===== 工具 =====

    private int page(ItemQuery q) {
        return q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();
    }

    private int pageSize(ItemQuery q) {
        int s = q.getSize() == null ? 20 : q.getSize();
        return s < 1 ? 20 : Math.min(s, 50);
    }

    private Page<ItemVO> emptyPage(ItemQuery q) {
        Page<ItemVO> p = new Page<>(page(q), pageSize(q), 0);
        p.setRecords(List.of());
        return p;
    }
}
