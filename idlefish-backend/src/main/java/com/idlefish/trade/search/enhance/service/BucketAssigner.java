package com.idlefish.trade.search.enhance.service;

import java.util.Objects;

/**
 * AB 稳定分桶纯函数（ITER-F04 / REQ-06）。
 * <p>
 * 设计要点：
 * 1. <b>稳定性</b>：基于用户标识的确定性哈希（FNV-1a 32 位）取模，同一 userId 在实验生命周期内
 *    恒分配到同一分组（对照/实验），跨设备一致，避免同一用户在不同请求间被反复切换分组导致的
 *    体验抖动与指标污染。
 * 2. <b>无副作用</b>：纯函数，不读写缓存/DB，便于离线单测与精准断言。
 * 3. <b>流量约束</b>：treatmentPercent 限定实验组流量占比，剩余流量恒落对照组，对照组永远保有基准。
 *
 * <p>分组含义：{@link Group#CONTROL} 对照组（基准策略）、{@link Group#TREATMENT} 实验组（新策略）。
 */
public final class BucketAssigner {

    private BucketAssigner() {
    }

    /** 实验分组：对照组 / 实验组。 */
    public enum Group {
        CONTROL,
        TREATMENT
    }

    /** FNV-1a 32 位哈希常量。 */
    private static final int FNV_OFFSET = 0x811c9dc5;
    private static final int FNV_PRIME = 0x01000193;

    /**
     * 稳定分桶（按 Long 用户 ID）。
     *
     * @param userId            用户 ID（同一逻辑用户跨设备一致）
     * @param treatmentPercent  实验组流量占比 0~100
     * @return 该用户所属分组；userId 为 null 时一律归对照组（未登录不进实验）
     */
    public static Group assign(Long userId, int treatmentPercent) {
        if (userId == null) {
            return Group.CONTROL;
        }
        return assign(String.valueOf(userId), treatmentPercent);
    }

    /**
     * 稳定分桶（按字符串用户标识，设备指纹/匿名 ID 亦可复用同一算法）。
     *
     * @param userKey           用户标识（非空时参与哈希；空白串归对照组）
     * @param treatmentPercent  实验组流量占比 0~100
     * @return 该用户所属分组
     */
    public static Group assign(String userKey, int treatmentPercent) {
        int percent = clampPercent(treatmentPercent);
        if (userKey == null || userKey.isBlank()) {
            return Group.CONTROL;
        }
        // hash ∈ [0,99]，< percent 落入实验组，否则对照组
        int bucket = Math.floorMod(fnv1a(userKey), 100);
        return bucket < percent ? Group.TREATMENT : Group.CONTROL;
    }

    /** 是否命中实验组（便捷封装）。 */
    public static boolean isInTreatment(Long userId, int treatmentPercent) {
        return assign(userId, treatmentPercent) == Group.TREATMENT;
    }

    /** 将占比约束到 [0,100]。 */
    private static int clampPercent(int p) {
        if (p < 0) {
            return 0;
        }
        return Math.min(p, 100);
    }

    /**
     * FNV-1a 32 位哈希（全确定性，跨 JVM 稳定）。
     * 用于把任意用户标识映射为均匀分布的整型，保证分桶长期稳定。
     */
    static int fnv1a(String s) {
        Objects.requireNonNull(s);
        int hash = FNV_OFFSET;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            // 等价 hash *= FNV_PRIME，但避免 int 溢出转 long 的额外开销
            hash += (hash << 1) + (hash << 4) + (hash << 7) + (hash << 8) + (hash << 24);
        }
        return hash;
    }
}
