package com.idlefish.trade.search.serving.dto;

/**
 * 检索/推荐结果封装（ITER-T04 / REQ-03/07）：在数据之外携带服务化元信息，
 * 供控制器标注数据时效与降级归因（响应头 / 状态端点），不改变既有响应体结构。
 *
 * @param <T> 数据类型（{@code Page<ItemVO>} / {@code List<ItemVO>}）
 */
public class ServingOutcome<T> {

    /** 实际数据（降级时可能为空集合，但绝不为 null）。 */
    private final T data;
    /** 数据时效：距最近一次成功索引同步的秒数；-1 表示未知。 */
    private final long dataAgeSeconds;
    /** 是否走了降级路径（远程失败回落内联 / 熔断跳过 / 兜底）。 */
    private final boolean degraded;
    /** 降级归因（degraded=true 时非空）：remote_failed / breaker_open / inline_failed / hot_fallback。 */
    private final String reason;
    /** 实际产出数据的模式：inline / remote。 */
    private final String mode;

    public ServingOutcome(T data, long dataAgeSeconds, boolean degraded, String reason, String mode) {
        this.data = data;
        this.dataAgeSeconds = dataAgeSeconds;
        this.degraded = degraded;
        this.reason = reason;
        this.mode = mode;
    }

    public static <T> ServingOutcome<T> ok(T data, long dataAgeSeconds, String mode) {
        return new ServingOutcome<>(data, dataAgeSeconds, false, null, mode);
    }

    public static <T> ServingOutcome<T> degraded(T data, long dataAgeSeconds, String reason, String mode) {
        return new ServingOutcome<>(data, dataAgeSeconds, true, reason, mode);
    }

    public T getData() {
        return data;
    }

    public long getDataAgeSeconds() {
        return dataAgeSeconds;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public String getReason() {
        return reason;
    }

    public String getMode() {
        return mode;
    }
}
