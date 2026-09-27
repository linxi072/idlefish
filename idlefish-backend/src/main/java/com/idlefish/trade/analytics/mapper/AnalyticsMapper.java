package com.idlefish.trade.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 运营 BI 聚合查询（F-15.5）。
 * <p>全部为只读聚合，按统计周期（created_at >= start）下推，避免把明细拉到应用层计算。
 * 金额列（pay_amount 等为 BIGINT，单位：分），COUNT/SUM 在 MySQL 侧返回 BigInteger，
 * 上层经 {@code AnalyticsCalculator}/Service 的 {@code toLong} 安全归一。</p>
 */
@Mapper
public interface AnalyticsMapper {

    /** 概览汇总：周期内 GMV（已支付/托管/运输/完成口径）、支付订单数、退款订单数、总订单数。 */
    @Select("SELECT "
            + " COALESCE(SUM(CASE WHEN status IN ('paid','pending_ship','shipping','completed') THEN pay_amount ELSE 0 END),0) AS gmv,"
            + " COUNT(CASE WHEN status IN ('paid','pending_ship','shipping','completed') THEN 1 END) AS paidOrderCount,"
            + " COUNT(CASE WHEN status IN ('refunding','refunded') THEN 1 END) AS refundOrderCount,"
            + " COUNT(*) AS totalOrderCount"
            + " FROM t_order WHERE created_at >= #{start}")
    Map<String, Object> overviewTotals(@Param("start") LocalDateTime start);

    /** 每日趋势：每日 GMV / 支付订单数 / 订单总数（按自然日分组）。 */
    @Select("SELECT DATE(created_at) AS day,"
            + " COALESCE(SUM(CASE WHEN status IN ('paid','pending_ship','shipping','completed') THEN pay_amount ELSE 0 END),0) AS gmv,"
            + " COUNT(CASE WHEN status IN ('paid','pending_ship','shipping','completed') THEN 1 END) AS paidCount,"
            + " COUNT(*) AS orderCount"
            + " FROM t_order WHERE created_at >= #{start} GROUP BY DATE(created_at) ORDER BY day")
    List<Map<String, Object>> dailyTrend(@Param("start") LocalDateTime start);

    /** 转化漏斗：统计各关键行为事件计数（浏览/搜索/收藏/下单/支付）。 */
    @Select("SELECT event, COUNT(*) AS cnt FROM t_track_event"
            + " WHERE created_at >= #{start} AND event IN ('view_item','search','favorite','order_create','pay')"
            + " GROUP BY event")
    List<Map<String, Object>> funnelCounts(@Param("start") LocalDateTime start);

    /** 品类 GMV 分布：订单→商品→类目 关联，按 GMV 降序。 */
    @Select("SELECT i.category_id AS categoryId, c.name AS categoryName,"
            + " COUNT(o.id) AS orderCount, COALESCE(SUM(o.pay_amount),0) AS gmv"
            + " FROM t_order o JOIN t_item i ON o.item_id = i.id LEFT JOIN t_category c ON i.category_id = c.id"
            + " WHERE o.status IN ('paid','pending_ship','shipping','completed') AND o.created_at >= #{start}"
            + " GROUP BY i.category_id, c.name ORDER BY gmv DESC")
    List<Map<String, Object>> categoryGmv(@Param("start") LocalDateTime start);

    /** 成色分布：在售 + 全部未删商品按成色等级计数。 */
    @Select("SELECT condition_level AS conditionLevel, COUNT(*) AS cnt FROM t_item"
            + " WHERE deleted = 0 GROUP BY condition_level ORDER BY conditionLevel")
    List<Map<String, Object>> conditionDist();

    @Select("SELECT COUNT(*) FROM t_user")
    long userCount();

    @Select("SELECT COUNT(*) FROM t_item WHERE deleted = 0 AND status = 'onsale'")
    long itemOnsaleCount();

    @Select("SELECT COUNT(*) FROM t_item WHERE deleted = 0")
    long itemTotalCount();
}
