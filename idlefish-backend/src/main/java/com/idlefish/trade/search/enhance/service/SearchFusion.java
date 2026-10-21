package com.idlefish.trade.search.enhance.service;

import com.idlefish.trade.item.vo.ItemVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * 多路召回融合纯函数（ITER-F04 / REQ-02 融合召回、REQ-03 降级不空结果）。
 * <p>
 * 职责：把关键词召回、同义词扩展召回（语义近似，沙箱降级实现）等多路结果合并为单一有序列表。
 * 本身不依赖任何外部组件，便于离线单测与精准断言。融合策略由上层传入打分函数决定，
 * 使本函数保持无状态、可复用。
 */
public final class SearchFusion {

    private SearchFusion() {
    }

    /**
     * 融合多路召回：按 id 去重（保留首次出现，即高优先级桶优先），再按打分降序排列。
     *
     * @param buckets 多路召回结果，列表顺序即优先级（下标小者优先，去重时保留其记录）
     * @param scorer 融合打分函数（分值越高越靠前；应已综合相关性/热度/实时兴趣/同城）
     * @return 去重并排序后的统一召回列表（不为 null）
     */
    public static List<ItemVO> fuse(List<List<ItemVO>> buckets, ToDoubleFunction<ItemVO> scorer) {
        if (buckets == null || buckets.isEmpty()) {
            return new ArrayList<>();
        }
        // 去重：保留首次出现（高优先级桶优先）
        Map<Long, ItemVO> dedup = new LinkedHashMap<>();
        for (List<ItemVO> bucket : buckets) {
            if (bucket == null) {
                continue;
            }
            for (ItemVO it : bucket) {
                if (it == null || it.getId() == null) {
                    continue;
                }
                dedup.putIfAbsent(it.getId(), it);
            }
        }
        List<ItemVO> merged = new ArrayList<>(dedup.values());
        merged.sort(Comparator.comparingDouble((ItemVO it) -> -safeScore(scorer, it)));
        return merged;
    }

    /**
     * 按 id 去重并保持首次出现顺序（供零结果兜底等仅需去重无需重排的场景复用）。
     */
    public static List<ItemVO> dedupe(List<ItemVO> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        Map<Long, ItemVO> dedup = new LinkedHashMap<>();
        for (ItemVO it : items) {
            if (it == null || it.getId() == null) {
                continue;
            }
            dedup.putIfAbsent(it.getId(), it);
        }
        return new ArrayList<>(dedup.values());
    }

    /** 截断前 N 条（N<=0 返回空列表，防御越界）。 */
    public static List<ItemVO> topN(List<ItemVO> items, int n) {
        if (items == null || n <= 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(items.subList(0, Math.min(n, items.size())));
    }

    private static double safeScore(ToDoubleFunction<ItemVO> scorer, ItemVO it) {
        if (scorer == null) {
            return 0d;
        }
        try {
            return scorer.applyAsDouble(it);
        } catch (RuntimeException ex) {
            // 单条打分异常不应污染整体召回，降级为 0 分保底
            return 0d;
        }
    }

    /** 便捷：两个列表融合（关键词 + 同义词扩展）。 */
    public static List<ItemVO> fuseTwo(List<ItemVO> primary, List<ItemVO> secondary,
                                       ToDoubleFunction<ItemVO> scorer) {
        List<List<ItemVO>> buckets = new ArrayList<>();
        buckets.add(primary == null ? new ArrayList<>() : primary);
        buckets.add(secondary == null ? new ArrayList<>() : secondary);
        return fuse(buckets, scorer);
    }
}
