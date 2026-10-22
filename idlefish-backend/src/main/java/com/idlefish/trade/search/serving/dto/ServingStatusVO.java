package com.idlefish.trade.search.serving.dto;

import java.util.Map;

/**
 * 搜推服务状态视图（ITER-T04 / REQ-04/07/08）：内联/远程双模式共用的状态快照，
 * 供 {@code /sr/api/status}（服务侧）与 {@code /api/admin/search-service/status}（运营侧）暴露。
 */
public class ServingStatusVO {

    /** 本部署配置的模式：inline / remote。 */
    private String mode;
    /** 客户端熔断器状态：CLOSED / OPEN / HALF_OPEN。 */
    private String breakerState;
    /** 数据时效（秒）：实体 → 距最近一次成功同步的秒数；-1 未知（从未同步）。 */
    private Map<String, Long> dataAgeSeconds;
    /** 隔离线程池快照：activeCount / poolSize / queueSize / completedTaskCount。 */
    private Map<String, Object> executor;
    /** 指标计数器快照（search.serving.* / search.sync.*）。 */
    private Map<String, Long> metrics;
    /** 快照时间（毫秒）。 */
    private long ts;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getBreakerState() {
        return breakerState;
    }

    public void setBreakerState(String breakerState) {
        this.breakerState = breakerState;
    }

    public Map<String, Long> getDataAgeSeconds() {
        return dataAgeSeconds;
    }

    public void setDataAgeSeconds(Map<String, Long> dataAgeSeconds) {
        this.dataAgeSeconds = dataAgeSeconds;
    }

    public Map<String, Object> getExecutor() {
        return executor;
    }

    public void setExecutor(Map<String, Object> executor) {
        this.executor = executor;
    }

    public Map<String, Long> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Long> metrics) {
        this.metrics = metrics;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }
}
