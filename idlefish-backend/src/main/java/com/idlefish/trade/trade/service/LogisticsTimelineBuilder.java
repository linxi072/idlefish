package com.idlefish.trade.trade.service;

import com.idlefish.trade.trade.vo.LogisticsTrack;
import com.idlefish.trade.trade.vo.OrderLogisticsVO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 物流时间轴纯函数构造器（F-14.5）。
 * <p>
 * 不依赖 Spring / 数据库，便于离线单测。职责：
 * 1) 物流公司代码 → 中文名；
 * 2) 物流状态 → 文案；
 * 3) 节点描述文本 → 节点类型（签收 / 异常 / 派送 / 运输 / 其他）；
 * 4) 将扁平轨迹 {@code [{time, desc}]} 组装为结构化 {@link OrderLogisticsVO}（最新在前）。
 */
public final class LogisticsTimelineBuilder {

    private LogisticsTimelineBuilder() {
    }

    /** 物流公司代码 → 中文名（未知代码原样返回）。 */
    public static String companyName(String company) {
        if (company == null || company.isBlank()) {
            return "未知";
        }
        return switch (company.toUpperCase()) {
            case "SF" -> "顺丰速运";
            case "YTO" -> "圆通速递";
            case "ZTO" -> "中通快递";
            case "YD" -> "韵达速递";
            case "HTKY" -> "百世快递";
            case "JD" -> "京东物流";
            case "EMS" -> "EMS";
            default -> company;
        };
    }

    /** 物流状态 → 文案（缺省视为运输中）。 */
    public static String statusText(String status) {
        return switch (status == null ? "" : status) {
            case "signed" -> "已签收";
            case "exception" -> "异常";
            default -> "运输中";
        };
    }

    /**
     * 节点类型识别（基于描述文本关键词）。
     * sign=签收 / exception=异常 / transit=派送中 / transport=运输中 / unknown=其他。
     */
    public static String classify(String desc) {
        if (desc == null) {
            return "unknown";
        }
        if (desc.contains("签收") || desc.contains("已签") || desc.contains("代收") || desc.contains("本人收")) {
            return "sign";
        }
        if (desc.contains("异常") || desc.contains("拒收") || desc.contains("退回") || desc.contains("破损")
                || desc.contains("疑难") || desc.contains("滞留")) {
            return "exception";
        }
        if (desc.contains("派送") || desc.contains("派件") || desc.contains("正在派") || desc.contains("投递")
                || desc.contains("派达")) {
            return "transit";
        }
        if (desc.contains("揽收") || desc.contains("已发") || desc.contains("运输中") || desc.contains("发出")
                || desc.contains("启运") || desc.contains("发往") || desc.contains("到达") || desc.contains("中转")) {
            return "transport";
        }
        return "unknown";
    }

    /**
     * 组装结构化轨迹。
     *
     * @param nodes  扁平轨迹（[{time, desc}]，可为空）
     * @param company 物流公司代码（可为空）
     * @param status  物流状态（可为空，缺省按运输中）
     * @return 结构化视图（tracks 最新在前）
     */
    public static OrderLogisticsVO build(List<Map<String, String>> nodes, String company, String status) {
        OrderLogisticsVO vo = new OrderLogisticsVO();
        vo.setCompany(company);
        vo.setCompanyName(companyName(company));
        vo.setStatus(status);
        vo.setStatusText(statusText(status));

        List<LogisticsTrack> tracks = new ArrayList<>();
        if (nodes != null) {
            for (Map<String, String> n : nodes) {
                LogisticsTrack t = new LogisticsTrack();
                t.setTime(n == null ? null : n.get("time"));
                t.setDesc(n == null ? null : n.get("desc"));
                t.setType(classify(t.getDesc()));
                tracks.add(t);
            }
            // 最新节点在前（贴合物流详情页阅读习惯）
            Collections.reverse(tracks);
        }
        vo.setTracks(tracks);
        return vo;
    }
}
