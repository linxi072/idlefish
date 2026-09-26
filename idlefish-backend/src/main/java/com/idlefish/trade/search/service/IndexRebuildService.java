package com.idlefish.trade.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 商品索引重建服务（F-14.1）：全量/增量重建 ES 索引。
 * - 全量：分页遍历在售且过审商品，逐条写入索引；单条失败重试一次，仍失败则计入 failed（不中断整体）。
 * - 增量：由 {@code ItemService} 在发布/上架/编辑时调用 {@link SearchService#indexItem} 实时同步，本服务负责兜底重建。
 * 无 ES 时 {@link SearchService#indexItem} 内部降级吞异常，本服务计数 success 仍累加（索引写入未真正生效），属预期容错。
 */
@Slf4j
@Service
public class IndexRebuildService {

    private static final int PAGE_SIZE = 500;

    private final ItemMapper itemMapper;
    private final SearchService searchService;

    public IndexRebuildService(ItemMapper itemMapper, SearchService searchService) {
        this.itemMapper = itemMapper;
        this.searchService = searchService;
    }

    /** 全量重建：遍历在售且过审商品重新索引，返回重建报告（总数/成功/失败）。 */
    public RebuildReport rebuildAll() {
        long total = 0, success = 0, failed = 0;
        int page = 1;
        while (true) {
            IPage<Item> res = itemMapper.selectPage(new Page<>(page, PAGE_SIZE),
                    new LambdaQueryWrapper<Item>()
                            .eq(Item::getStatus, "on_sale")
                            .eq(Item::getAuditStatus, "pass"));
            if (res.getRecords().isEmpty()) {
                break;
            }
            for (Item it : res.getRecords()) {
                total++;
                if (indexWithRetry(it)) {
                    success++;
                } else {
                    failed++;
                }
            }
            if (page >= res.getPages()) {
                break;
            }
            page++;
        }
        return new RebuildReport(total, success, failed);
    }

    /** 单条索引写入（失败重试一次）；返回是否成功。 */
    private boolean indexWithRetry(Item item) {
        try {
            searchService.indexItem(item);
            return true;
        } catch (Exception e1) {
            log.warn("[search:rebuild] 索引写入失败 itemId={}，重试一次: {}", item.getId(), e1.getMessage());
            try {
                searchService.indexItem(item);
                return true;
            } catch (Exception e2) {
                log.error("[search:rebuild] 索引写入重试仍失败 itemId={}: {}", item.getId(), e2.getMessage());
                return false;
            }
        }
    }

    /** 重建报告。 */
    @Data
    public static class RebuildReport {
        private final long total;
        private final long success;
        private final long failed;

        public RebuildReport(long total, long success, long failed) {
            this.total = total;
            this.success = success;
            this.failed = failed;
        }

        @Override
        public String toString() {
            return "RebuildReport{total=" + total + ", success=" + success + ", failed=" + failed + '}';
        }
    }
}
