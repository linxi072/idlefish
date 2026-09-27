package com.idlefish.trade.marketing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 券营销聚合查询（F-13.5）。
 * <p>只读聚合，全部下推 MySQL；COUNT/SUM 在 MySQL 侧返回 BigInteger，上层经
 * {@code AdminCouponAnalyticsService#toLong} 安全归一。金额单位为分。</p>
 */
@Mapper
public interface CouponAnalyticsMapper {

    /** 发放（领取）券总数。 */
    @Select("SELECT COUNT(*) FROM t_user_coupon")
    long issuedCount();

    /** 已核销券数。 */
    @Select("SELECT COUNT(*) FROM t_user_coupon WHERE status = 'USED'")
    long redeemedCount();

    /** 核销关联订单数（去重）。 */
    @Select("SELECT COUNT(DISTINCT order_no) FROM t_user_coupon"
            + " WHERE status = 'USED' AND order_no IS NOT NULL")
    long redeemedOrderCount();

    /** 核销带来的成交额（分）：核销券关联订单实付金额求和。 */
    @Select("SELECT COALESCE(SUM(o.pay_amount),0) FROM t_user_coupon uc"
            + " LEFT JOIN t_order o ON uc.order_no = o.order_no WHERE uc.status = 'USED'")
    long redeemedGmv();

    /** 核销优惠成本（分）：核销券落库的 discount_amount 求和。 */
    @Select("SELECT COALESCE(SUM(discount_amount),0) FROM t_user_coupon WHERE status = 'USED'")
    long discountCost();

    /** 券类型发放分布：按券模板类型统计领取数。 */
    @Select("SELECT c.type AS type, COUNT(uc.id) AS cnt FROM t_user_coupon uc"
            + " JOIN t_coupon c ON uc.coupon_id = c.id GROUP BY c.type ORDER BY cnt DESC")
    List<Map<String, Object>> typeDistribution();
}
