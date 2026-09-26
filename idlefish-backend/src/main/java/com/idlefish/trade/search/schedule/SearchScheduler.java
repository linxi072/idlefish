package com.idlefish.trade.search.schedule;

import com.idlefish.trade.common.observability.TraceContext;
import com.idlefish.trade.search.service.IndexRebuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 搜索域定时任务（F-14.1）：每日凌晨对商品索引做全量重建兜底，弥补实时增量同步可能遗漏的文档。
 * 每个调度入口先 {@link TraceContext#ensure()} 生成 traceId，使后台任务日志可统一追踪（F-12.4）。
 */
@Component
public class SearchScheduler {

    private static final Logger log = LoggerFactory.getLogger(SearchScheduler.class);

    private final IndexRebuildService rebuildService;

    public SearchScheduler(IndexRebuildService rebuildService) {
        this.rebuildService = rebuildService;
    }

    /** 每日 03:30：全量重建商品索引（失败重试 + 容错，异常不阻断）。 */
    @Scheduled(cron = "0 30 3 * * *")
    public void rebuildDaily() {
        TraceContext.ensure();
        try {
            IndexRebuildService.RebuildReport report = rebuildService.rebuildAll();
            log.info("[search:scheduler] 索引重建完成: {}", report);
        } catch (Exception e) {
            log.error("[search:scheduler] 索引重建失败", e);
        }
    }
}
