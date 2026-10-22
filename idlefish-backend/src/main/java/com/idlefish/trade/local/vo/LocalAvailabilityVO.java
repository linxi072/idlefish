package com.idlefish.trade.local.vo;

import lombok.Data;

/**
 * 同城履约可用性视图（ITER-F05 / REQ-01、REQ-09）。
 *
 * <p>纪律：不可用时必须给出明确原因（{@code available=false} + {@code reason}），
 * 前端据此展示「该城市暂未开通」而非留白或假成功。
 */
@Data
public class LocalAvailabilityVO {

    /** 城市是否开通同城服务。 */
    private boolean available;

    /** 城市是否支持上门回收。 */
    private boolean recycleAvailable;

    /** 距离是否在可承接范围内。 */
    private boolean distanceOk;

    /** 第三方配送服务商是否已接入（REQ-09 未接入即明确不可用）。 */
    private boolean providerReady;

    /** 预估配送费（分）。 */
    private long freightFen;

    /** 不可用原因聚合（可用于直接提示用户）。 */
    private String reason;

    public static LocalAvailabilityVO unavailable(String reason) {
        LocalAvailabilityVO v = new LocalAvailabilityVO();
        v.setAvailable(false);
        v.setRecycleAvailable(false);
        v.setDistanceOk(false);
        v.setFreightFen(0L);
        v.setReason(reason);
        return v;
    }
}
