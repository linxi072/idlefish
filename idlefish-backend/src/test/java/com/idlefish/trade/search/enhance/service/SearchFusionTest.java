package com.idlefish.trade.search.enhance.service;

import com.idlefish.trade.item.vo.ItemVO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多路召回融合纯函数单测（ITER-F04 / REQ-02 融合召回、REQ-03 降级不空结果）。
 */
class SearchFusionTest {

    private ItemVO item(long id) {
        ItemVO v = new ItemVO();
        v.setId(id);
        return v;
    }

    @Test
    @DisplayName("fuseTwo：跨路去重保留高优先级桶（主召回）记录")
    void dedupeKeepPrimary() {
        ItemVO a1 = item(1);
        ItemVO a2 = item(2);
        ItemVO b1 = item(1); // 与 a1 同 id，应去重并保留 a1
        ItemVO b3 = item(3);
        List<ItemVO> fused = SearchFusion.fuseTwo(List.of(a1, a2), List.of(b1, b3), it -> 0d);
        assertEquals(3, fused.size());
        assertEquals(1L, fused.get(0).getId());
        assertEquals(a1, fused.get(0)); // 保留首次出现（主路）
    }

    @Test
    @DisplayName("fuse：按打分降序排列")
    void sortByScoreDesc() {
        ItemVO low = item(1);
        ItemVO high = item(2);
        ItemVO mid = item(3);
        List<ItemVO> fused = SearchFusion.fuse(List.of(List.of(low, high, mid)),
                it -> scoreOf(it));
        assertEquals(3, fused.size());
        assertEquals(2L, fused.get(0).getId());
        assertEquals(3L, fused.get(1).getId());
        assertEquals(1L, fused.get(2).getId());
    }

    private double scoreOf(ItemVO it) {
        return switch ((int) (long) it.getId()) {
            case 1 -> 1;
            case 2 -> 9;
            case 3 -> 5;
            default -> 0;
        };
    }

    @Test
    @DisplayName("打分函数抛异常降级为 0 分，不污染整体召回")
    void scoreExceptionSafe() {
        ItemVO ok = item(1);
        ItemVO bad = item(2);
        List<ItemVO> fused = SearchFusion.fuseTwo(List.of(ok), List.of(bad),
                it -> {
                    if (it.getId() == 2L) {
                        throw new RuntimeException("boom");
                    }
                    return 1d;
                });
        assertEquals(2, fused.size());
    }

    @Test
    @DisplayName("空输入返回空列表，不抛异常（REQ-03 降级安全）")
    void emptySafe() {
        assertTrue(SearchFusion.fuse(null, it -> 1d).isEmpty());
        assertTrue(SearchFusion.fuseTwo(null, null, it -> 1d).isEmpty());
        assertTrue(SearchFusion.dedupe(null).isEmpty());
        assertTrue(SearchFusion.topN(null, 5).isEmpty());
    }

    @Test
    @DisplayName("topN 截断且不超过原长度")
    void topNTruncate() {
        List<ItemVO> items = List.of(item(1), item(2), item(3));
        assertEquals(2, SearchFusion.topN(items, 2).size());
        assertEquals(3, SearchFusion.topN(items, 99).size());
        assertTrue(SearchFusion.topN(items, 0).isEmpty());
    }

    @Test
    @DisplayName("dedupe：相同 id 仅保留首次出现")
    void dedupeById() {
        ItemVO a = item(1);
        ItemVO b = item(1);
        ItemVO c = item(2);
        assertEquals(2, SearchFusion.dedupe(List.of(a, b, c)).size());
    }
}
