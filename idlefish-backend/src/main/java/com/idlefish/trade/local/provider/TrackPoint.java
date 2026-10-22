package com.idlefish.trade.local.provider;

import lombok.Data;

/**
 * 第三方回传的轨迹节点（ITER-F05 / REQ-06）。
 * 归一化后写入既有 {@code t_logistics.detailJson}，再经 {@code LogisticsTimelineBuilder} 渲染。
 */
@Data
public class TrackPoint {

    /** 节点时间字符串（保持服务商原格式，与既有轨迹一致）。 */
    private String time;

    /** 节点描述。 */
    private String desc;
}
