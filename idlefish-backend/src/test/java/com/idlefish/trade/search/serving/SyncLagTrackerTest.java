package com.idlefish.trade.search.serving;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 索引同步滞后跟踪器单测（ITER-T04 / REQ-07）：滞后计算 / 超阈值判定 / 失败累计 / 快照。
 */
class SyncLagTrackerTest {

    @Test
    @DisplayName("从未同步：滞后返回 -1（未知），不误判超阈值")
    void unknownLag() {
        SyncLagTracker t = new SyncLagTracker();
        assertEquals(-1L, t.lagSeconds(SyncLagTracker.ENTITY_ITEM));
        assertFalse(t.isStale(SyncLagTracker.ENTITY_ITEM, 60L));
    }

    @Test
    @DisplayName("记录成功：滞后归零；指定时间戳回填按差值计算")
    void lagComputation() {
        SyncLagTracker t = new SyncLagTracker();
        t.recordSuccess(SyncLagTracker.ENTITY_ITEM);
        assertEquals(0L, t.lagSeconds(SyncLagTracker.ENTITY_ITEM));

        long fiveSecondsAgo = System.currentTimeMillis() - 5_000L;
        t.recordSuccessAt(SyncLagTracker.ENTITY_ITEM, fiveSecondsAgo);
        long lag = t.lagSeconds(SyncLagTracker.ENTITY_ITEM);
        assertTrue(lag >= 4 && lag <= 6, "滞后应在 4~6s 区间，实际 " + lag);
    }

    @Test
    @DisplayName("超阈值判定：lag > threshold 才判过期")
    void staleThreshold() {
        SyncLagTracker t = new SyncLagTracker();
        t.recordSuccessAt(SyncLagTracker.ENTITY_ITEM, System.currentTimeMillis() - 120_000L);
        assertTrue(t.isStale(SyncLagTracker.ENTITY_ITEM, 60L));
        assertFalse(t.isStale(SyncLagTracker.ENTITY_ITEM, 180L));
    }

    @Test
    @DisplayName("失败累计与清零：成功后清零，快照含两实体")
    void failuresAndSnapshot() {
        SyncLagTracker t = new SyncLagTracker();
        t.recordFailure(SyncLagTracker.ENTITY_ITEM);
        t.recordFailure(SyncLagTracker.ENTITY_ITEM);
        assertEquals(2L, t.failures(SyncLagTracker.ENTITY_ITEM));
        t.recordSuccess(SyncLagTracker.ENTITY_ITEM);
        assertEquals(0L, t.failures(SyncLagTracker.ENTITY_ITEM));

        Map<String, Object> snap = t.snapshot();
        assertTrue(snap.containsKey(SyncLagTracker.ENTITY_ITEM));
        assertTrue(snap.containsKey(SyncLagTracker.ENTITY_FEATURE));
    }
}
