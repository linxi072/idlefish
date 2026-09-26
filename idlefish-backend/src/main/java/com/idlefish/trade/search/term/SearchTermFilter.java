package com.idlefish.trade.search.term;

import java.util.Set;

/**
 * 搜索词归一化与屏蔽过滤（纯函数，无副作用，便于离线单测）。
 */
public final class SearchTermFilter {

    private SearchTermFilter() {
    }

    /** 归一化：去首尾空白、折叠连续空白、转小写；null/纯空白返回空串。 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().replaceAll("\\s+", " ");
        return s.toLowerCase();
    }

    /** 命中屏蔽词集合（大小写不敏感）。 */
    public static boolean isBlocked(String word, Set<String> blockWords) {
        if (word == null || blockWords == null) {
            return false;
        }
        return blockWords.contains(normalize(word));
    }
}
