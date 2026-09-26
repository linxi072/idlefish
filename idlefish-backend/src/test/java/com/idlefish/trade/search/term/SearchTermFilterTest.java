package com.idlefish.trade.search.term;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 搜索词归一化与屏蔽过滤纯函数单测（F-14.3，离线可跑）。
 */
class SearchTermFilterTest {

    @Test
    @DisplayName("normalize：去空白、折叠、转小写")
    void normalize() {
        assertEquals("iphone pro", SearchTermFilter.normalize("  iPhone  Pro "));
        assertEquals("", SearchTermFilter.normalize(null));
        assertEquals("", SearchTermFilter.normalize("   "));
        assertEquals("华为", SearchTermFilter.normalize(" 华为 "));
    }

    @Test
    @DisplayName("isBlocked：大小写不敏感命中屏蔽词集合")
    void isBlocked() {
        Set<String> block = new HashSet<>();
        block.add("iphone");
        assertTrue(SearchTermFilter.isBlocked("iPhone", block));
        assertTrue(SearchTermFilter.isBlocked(" iphone ", block));
        assertFalse(SearchTermFilter.isBlocked("samsung", block));
        assertFalse(SearchTermFilter.isBlocked(null, block));
    }
}
