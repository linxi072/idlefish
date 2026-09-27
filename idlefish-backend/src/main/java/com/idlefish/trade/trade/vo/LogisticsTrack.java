package com.idlefish.trade.trade.vo;

import lombok.Data;

/**
 * 物流轨迹节点（时间轴单元）。
 * 由 {@link LogisticsTimelineBuilder} 从扁平轨迹 {@code [{time, desc}]} 识别节点类型后构造。
 */
@Data
public class LogisticsTrack {

    /** 节点时间（已按 DateTimeUtil.FMT 格式化）。 */
    private String time;

    /** 节点描述（原始物流商文本或兜底文本）。 */
    private String desc;

    /**
     * 节点类型，用于前端着色：
     * sign=签收 / exception=异常 / transit=派送中 / transport=运输中 / unknown=其他。
     */
    private String type;
}
