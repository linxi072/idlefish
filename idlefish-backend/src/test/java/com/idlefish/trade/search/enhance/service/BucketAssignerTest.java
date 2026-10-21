package com.idlefish.trade.search.enhance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AB 稳定分桶纯函数单测（ITER-F04 / REQ-06）：确定性、稳定性、null 安全、占比约束。
 */
class BucketAssignerTest {

    @Test
    @DisplayName("null/空白用户标识一律归对照组（未登录不进实验）")
    void nullOrBlankGoControl() {
        assertEquals(BucketAssigner.Group.CONTROL, BucketAssigner.assign((Long) null, 50));
        assertEquals(BucketAssigner.Group.CONTROL, BucketAssigner.assign((String) null, 50));
        assertEquals(BucketAssigner.Group.CONTROL, BucketAssigner.assign("   ", 50));
    }

    @Test
    @DisplayName("占比 0 → 恒对照组；占比 100 → 恒实验组（对照组永远保有基准）")
    void percentExtremes() {
        for (long u = 1; u <= 200; u++) {
            assertEquals(BucketAssigner.Group.CONTROL, BucketAssigner.assign(u, 0), "占比0应恒对照");
            assertEquals(BucketAssigner.Group.TREATMENT, BucketAssigner.assign(u, 100), "占比100应恒实验");
        }
    }

    @Test
    @DisplayName("同一用户多次分桶稳定一致（跨调用 / Long 与 String 标识不漂移）")
    void stableAcrossCalls() {
        for (long u = 1; u <= 50; u++) {
            BucketAssigner.Group g1 = BucketAssigner.assign(u, 30);
            BucketAssigner.Group g2 = BucketAssigner.assign(u, 30);
            BucketAssigner.Group g3 = BucketAssigner.assign(String.valueOf(u), 30);
            assertEquals(g1, g2, "同一 Long 用户多次分桶应一致");
            assertEquals(g1, g3, "Long 与 String 标识应得出同一分组");
        }
    }

    @Test
    @DisplayName("占比约束到 [0,100]（负值等价于 0、超界等价于 100）")
    void clampPercent() {
        for (long u = 1; u <= 50; u++) {
            assertEquals(BucketAssigner.Group.CONTROL, BucketAssigner.assign(u, -10), "负占比应视为 0");
            assertEquals(BucketAssigner.Group.TREATMENT, BucketAssigner.assign(u, 999), "超界占比应视为 100");
        }
    }

    @Test
    @DisplayName("分组分布近似等于配置占比（大样本 FNV-1a 均匀性）")
    void distributionApprox() {
        int percent = 30;
        int total = 10000;
        int treatment = 0;
        for (long u = 1; u <= total; u++) {
            if (BucketAssigner.isInTreatment(u, percent)) {
                treatment++;
            }
        }
        double actual = (treatment * 100.0) / total;
        assertTrue(actual >= 25 && actual <= 35, "实验组占比应≈30%，实际=" + actual + "%");
    }
}
