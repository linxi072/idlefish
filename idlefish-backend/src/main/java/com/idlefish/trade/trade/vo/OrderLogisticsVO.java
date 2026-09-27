package com.idlefish.trade.trade.vo;

import lombok.Data;

import java.util.List;

/**
 * 订单物流视图（F-14.5 物流轨迹可视化）。
 * 将扁平轨迹升级为结构化时间轴，供小程序 / PC 后台订单详情渲染。
 */
@Data
public class OrderLogisticsVO {

    /** 运单号。 */
    private String logisticsNo;

    /** 物流公司代码（如 SF）。 */
    private String company;

    /** 物流公司中文名（由代码映射）。 */
    private String companyName;

    /** 物流状态：transport / signed / exception。 */
    private String status;

    /** 物流状态文案：运输中 / 已签收 / 异常。 */
    private String statusText;

    /** 时间轴轨迹（最新节点在前）。 */
    private List<LogisticsTrack> tracks;
}
