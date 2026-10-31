package com.idlefish.trade.local.service;

import com.idlefish.trade.common.util.DateTimeUtil;
import com.idlefish.trade.local.provider.TrackPoint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同城配送纯函数层（ITER-F05）。仅包含无副作用、无数据访问的计算与映射辅助，
 * 由 {@link LocalDeliveryService} 与 {@link LocalDeliveryStore} 共享，便于单测隔离。
 */
public final class LocalDeliveryViews {

    private LocalDeliveryViews() {
    }

    /** 业务类型是否为订单（订单同城配送需写入既有 t_logistics 轨迹）。 */
    public static boolean isOrderBiz(String bizType) {
        return "ORDER".equalsIgnoreCase(bizType);
    }

    /** 同城配送状态 → 物流时间轴状态（transport/signed/exception）。 */
    public static String toLogisticsStatus(String status) {
        if (status == null) {
            return "transport";
        }
        return switch (status) {
            case "SIGNED" -> "signed";
            case "FAILED", "CANCELED" -> "exception";
            default -> "transport";
        };
    }

    /** 构造单个轨迹节点（time 按 FMT 格式化）。 */
    public static Map<String, String> node(LocalDateTime t, String desc) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("time", t.format(DateTimeUtil.FMT));
        m.put("desc", desc);
        return m;
    }

    /** 第三方轨迹点归一化为结构化节点列表（null 安全）。 */
    public static List<Map<String, String>> toNodes(List<TrackPoint> points) {
        List<Map<String, String>> nodes = new ArrayList<>();
        if (points == null) {
            return nodes;
        }
        for (TrackPoint p : points) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("time", p.getTime());
            m.put("desc", p.getDesc());
            nodes.add(m);
        }
        return nodes;
    }
}
