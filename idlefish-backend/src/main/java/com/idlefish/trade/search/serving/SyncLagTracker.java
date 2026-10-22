package com.idlefish.trade.search.serving;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 索引同步滞后跟踪器（ITER-T04 / REQ-07）：记录各实体（item / feature）最近一次成功同步的时间戳，
 * 计算「数据变更 → 可检索」的滞后秒数，供告警评估与响应数据时效标注。
 * <p>
 * 线程安全：ConcurrentHashMap + volatile 语义的时间戳；单测可直接验证计算逻辑。
 * 滞后 = -1 表示从未成功同步（冷启动/全量重建前），调用方据此判未知而非零滞后。
 */
public class SyncLagTracker {

    /** 实体类型常量（当前同步 item 索引；feature 实时特征沿用 F04 Redis 窗口）。 */
    public static final String ENTITY_ITEM = "item";
    public static final String ENTITY_FEATURE = "feature";

    private final ConcurrentHashMap<String, Long> lastSuccessAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> failureCount = new ConcurrentHashMap<>();

    /** 记录一次成功同步（当前时间）。 */
    public void recordSuccess(String entity) {
        lastSuccessAt.put(entity, System.currentTimeMillis());
        failureCount.remove(entity);
    }

    /** 以指定时间记录成功（远程模式轮询到服务侧时间戳时回填，保持滞后口径一致）。 */
    public void recordSuccessAt(String entity, long epochMillis) {
        lastSuccessAt.put(entity, epochMillis);
        failureCount.remove(entity);
    }

    /** 记录一次同步失败（累计，供告警归因）。 */
    public void recordFailure(String entity) {
        failureCount.merge(entity, 1L, Long::sum);
    }

    /**
     * 滞后秒数：now - 最近成功同步时间；从未同步返回 -1（未知）。
     * 时钟回拨等异常情况下返回 0（不产生负滞后误导告警）。
     */
    public long lagSeconds(String entity) {
        Long at = lastSuccessAt.get(entity);
        if (at == null) {
            return -1L;
        }
        long lag = (System.currentTimeMillis() - at) / 1000L;
        return Math.max(lag, 0L);
    }

    /** 是否滞后超阈值（未知滞后不判超阈值，避免冷启动误报）。 */
    public boolean isStale(String entity, long thresholdSeconds) {
        long lag = lagSeconds(entity);
        return lag >= 0 && lag > thresholdSeconds;
    }

    /** 累计失败次数。 */
    public long failures(String entity) {
        return failureCount.getOrDefault(entity, 0L);
    }

    /** 全量快照（状态端点 / 告警日志）。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String entity : new String[]{ENTITY_ITEM, ENTITY_FEATURE}) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("lagSeconds", lagSeconds(entity));
            e.put("failures", failures(entity));
            out.put(entity, e);
        }
        return out;
    }

    /** 最近成功同步时间（毫秒；无则 null）。 */
    public Long lastSuccessAt(String entity) {
        return lastSuccessAt.get(entity);
    }
}
