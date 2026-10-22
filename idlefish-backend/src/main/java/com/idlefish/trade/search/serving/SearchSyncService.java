package com.idlefish.trade.search.serving;

import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.search.service.SearchService;
import com.idlefish.trade.search.serving.dto.ItemSyncRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 搜推索引同步编排（ITER-T04 / REQ-02/07）：商品变更的统一收口。
 * <p>
 * 双写流水线（过渡期一致，PRD §八.4）：
 * ① {@link SearchSyncProducer} 异步投递 RocketMQ 消息 → 远程搜推服务消费（未配置接入点时跳过）；
 * ② 本地索引即时更新（双写保持本地 ES 热备，切流/回滚均可无缝）；
 * ③ 成功/失败记入 {@link SyncLagTracker}，滞后超阈值由 {@link SearchSyncMonitor} 告警（REQ-07）。
 * <p>
 * 纪律：任一步失败仅计数与日志，绝不阻断商品主流程（最终一致语义，PRD §八.5）。
 */
@Service
public class SearchSyncService {

    private static final Logger log = LoggerFactory.getLogger(SearchSyncService.class);

    /** 同步事件类型。 */
    public static final String EVENT_UPSERT = "UPSERT";
    public static final String EVENT_REMOVE = "REMOVE";

    private final SearchSyncProducer producer;
    private final SearchService searchService;
    private final ItemMapper itemMapper;
    private final SyncLagTracker lagTracker;
    private final MetricsRegistry metrics;

    public SearchSyncService(SearchSyncProducer producer, SearchService searchService,
                             ItemMapper itemMapper, SyncLagTracker lagTracker, MetricsRegistry metrics) {
        this.producer = producer;
        this.searchService = searchService;
        this.itemMapper = itemMapper;
        this.lagTracker = lagTracker;
        this.metrics = metrics;
    }

    /**
     * 商品数据变更入口（由 ItemService 在审核通过/编辑/下架/删除时调用）。
     *
     * @param itemId 商品 ID
     * @param event  UPSERT / REMOVE
     */
    public void onItemChanged(Long itemId, String event) {
        if (itemId == null) {
            return;
        }
        // ① 异步消息投递（REQ-02，best-effort）
        try {
            producer.publishItemEvent(itemId, event);
        } catch (RuntimeException e) {
            metrics.increment("search.sync.publish.unexpected");
            log.warn("[search:sync] 消息投递异常（已吞掉）itemId={}: {}", itemId, e.getMessage());
        }
        // ② 本地索引双写 + 滞后基线（REQ-07 度量起点）
        try {
            applyLocal(itemId, event);
            lagTracker.recordSuccess(SyncLagTracker.ENTITY_ITEM);
            metrics.increment("search.sync.apply.success");
        } catch (RuntimeException e) {
            lagTracker.recordFailure(SyncLagTracker.ENTITY_ITEM);
            metrics.increment("search.sync.apply.failure");
            log.warn("[search:sync] 本地索引更新失败（滞后将累计，等待补偿）itemId={} event={}: {}",
                    itemId, event, e.getMessage());
        }
    }

    /**
     * 消费侧同步应用（{@code /sr/api/sync/item} 回调 / 补偿推送入口）：
     * 远程消息消费或运营补偿时调用，更新本地索引并刷新滞后基线。
     */
    public void applySyncedItem(ItemSyncRequest req) {
        if (req == null || req.getItemId() == null) {
            return;
        }
        try {
            applyLocal(req.getItemId(), req.getEvent());
            // 以事件发生时间回填滞后口径（消费延迟 = now - occurredAt）
            if (req.getOccurredAt() != null && req.getOccurredAt() > 0) {
                lagTracker.recordSuccessAt(SyncLagTracker.ENTITY_ITEM, req.getOccurredAt());
            } else {
                lagTracker.recordSuccess(SyncLagTracker.ENTITY_ITEM);
            }
            metrics.increment("search.sync.consume.success");
        } catch (RuntimeException e) {
            lagTracker.recordFailure(SyncLagTracker.ENTITY_ITEM);
            metrics.increment("search.sync.consume.failure");
            log.warn("[search:sync] 消费侧索引更新失败 itemId={}: {}", req.getItemId(), e.getMessage());
            throw e;
        }
    }

    /** 本地索引写入（双写核心）。 */
    private void applyLocal(Long itemId, String event) {
        if (EVENT_REMOVE.equals(event)) {
            searchService.removeItem(itemId);
        } else {
            Item item = itemMapper.selectById(itemId);
            if (item == null) {
                // 商品已不存在：按移除处理，避免脏文档残留
                searchService.removeItem(itemId);
                return;
            }
            searchService.indexItem(item);
        }
    }
}
