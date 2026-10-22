package com.idlefish.trade.search.serving;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.observability.TraceContext;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.search.serving.dto.ServingStatusVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 索引同步滞后监控（ITER-T04 / REQ-07）：
 * ① 周期性评估 {@link SyncLagTracker}，滞后超阈值时记数 + 告警管理员（带冷却，避免轰炸）；
 * ② 远程模式下周期性拉取搜推服务 {@code /sr/api/status}，以服务侧口径回填滞后
 *    （消费延迟真实反映「变更 → 可检索」时延）；
 * ③ 所有任务先 {@link TraceContext#ensure()} 生成 traceId，日志可统一追踪（F-12.4）。
 */
@Component
public class SearchSyncMonitor {

    private static final Logger log = LoggerFactory.getLogger(SearchSyncMonitor.class);

    /** 监控周期（毫秒）。 */
    private static final long INTERVAL_MS = 30_000L;
    /** 告警冷却（毫秒）：同类实体滞后告警在冷却期内不重复发送。 */
    private static final long ALERT_COOLDOWN_MS = 300_000L;

    private final IdlefishProperties props;
    private final SyncLagTracker lagTracker;
    private final HttpSearchRecommendGateway remote;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;

    /** 实体 → 最近一次滞后告警时间（冷却控制）。 */
    private final ConcurrentHashMap<String, Long> lastAlertAt = new ConcurrentHashMap<>();
    /** 最近一次远程状态拉取时间。 */
    private volatile long lastRemotePollAt = 0L;

    public SearchSyncMonitor(IdlefishProperties props, SyncLagTracker lagTracker,
                             HttpSearchRecommendGateway remote,
                             NotificationService notificationService,
                             MetricsRegistry metrics) {
        this.props = props;
        this.lagTracker = lagTracker;
        this.remote = remote;
        this.notificationService = notificationService;
        this.metrics = metrics;
    }

    private boolean isRemote() {
        return props.getSearchService() != null
                && props.getSearchService().getMode() == IdlefishProperties.Mode.REMOTE;
    }

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = 20_000L)
    public void check() {
        TraceContext.ensure();
        // 远程模式：先以服务侧口径刷新滞后（失败不影响后续本地评估）
        if (isRemote()) {
            pollRemoteStatus();
        }
        long threshold = props.getSearchService() == null
                ? 60L : props.getSearchService().getLagAlertSeconds();
        for (String entity : new String[]{SyncLagTracker.ENTITY_ITEM, SyncLagTracker.ENTITY_FEATURE}) {
            evaluate(entity, threshold);
        }
    }

    /** 滞后评估：超阈值记数 + 冷却告警。 */
    private void evaluate(String entity, long thresholdSeconds) {
        if (!lagTracker.isStale(entity, thresholdSeconds)) {
            return;
        }
        long lag = lagTracker.lagSeconds(entity);
        metrics.increment("search.sync.lag.alert");
        log.warn("[search:sync] 索引同步滞后超阈值 entity={} lag={}s threshold={}s failures={}",
                entity, lag, thresholdSeconds, lagTracker.failures(entity));
        long now = System.currentTimeMillis();
        Long last = lastAlertAt.get(entity);
        if (last == null || now - last >= ALERT_COOLDOWN_MS) {
            lastAlertAt.put(entity, now);
            notifyLag(entity, lag, thresholdSeconds);
        }
    }

    /** 远程模式：拉取服务状态回填滞后口径（best-effort）。 */
    private void pollRemoteStatus() {
        long now = System.currentTimeMillis();
        if (now - lastRemotePollAt < INTERVAL_MS) {
            return;
        }
        lastRemotePollAt = now;
        try {
            ServingStatusVO status = remote.status();
            metrics.increment("search.sync.remote_status.success");
            if (status != null && status.getDataAgeSeconds() != null) {
                for (Map.Entry<String, Long> e : status.getDataAgeSeconds().entrySet()) {
                    Long age = e.getValue();
                    if (age != null && age >= 0) {
                        // 服务侧 age 秒 → 本地时间轴上的成功时刻
                        lagTracker.recordSuccessAt(e.getKey(), now - age * 1000L);
                    }
                }
            }
        } catch (RuntimeException e) {
            metrics.increment("search.sync.remote_status.failure");
            log.warn("[search:sync] 远程状态拉取失败（沿用本地滞后口径）: {}", e.getMessage());
        }
    }

    /** 滞后告警触达管理员（SYSTEM_ALERT，best-effort）。 */
    private void notifyLag(String entity, long lagSeconds, long thresholdSeconds) {
        try {
            Long adminId = props.getSearchService() == null ? null : props.getSearchService().getAlertAdminUserId();
            if (adminId == null) {
                adminId = props.getNotify() == null ? null : props.getNotify().getAlertAdminUserId();
            }
            if (adminId == null) {
                adminId = 1L;
            }
            notificationService.notify(adminId, NotificationType.SYSTEM_ALERT,
                    "search-sync-lag:" + entity,
                    "搜推索引同步滞后",
                    "实体 " + entity + " 索引同步滞后 " + lagSeconds + "s（阈值 " + thresholdSeconds
                            + "s），请检查消息消费与索引写入，避免用户检索到过期数据。");
        } catch (RuntimeException e) {
            log.warn("[search:sync] 滞后告警发送失败: {}", e.getMessage());
        }
    }
}
