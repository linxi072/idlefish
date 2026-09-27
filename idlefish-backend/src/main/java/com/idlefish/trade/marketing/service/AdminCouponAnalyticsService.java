package com.idlefish.trade.marketing.service;

import com.idlefish.trade.marketing.CouponAnalyticsCalculator;
import com.idlefish.trade.marketing.mapper.CouponAnalyticsMapper;
import com.idlefish.trade.marketing.vo.CouponAnalyticsOverview;
import com.idlefish.trade.marketing.vo.CouponTypeDist;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 券营销聚合服务（F-13.5）。
 * <p>只读聚合，不直接写库；所有派生指标经 {@link CouponAnalyticsCalculator} 纯函数计算，便于单测。</p>
 */
@Service
public class AdminCouponAnalyticsService {

    private final CouponAnalyticsMapper couponAnalyticsMapper;

    public AdminCouponAnalyticsService(CouponAnalyticsMapper couponAnalyticsMapper) {
        this.couponAnalyticsMapper = couponAnalyticsMapper;
    }

    /** 券营销概览：发放量 / 核销率 / 核销订单数 / 核销 GMV / 优惠成本 / ROI。 */
    public CouponAnalyticsOverview overview() {
        long issued = couponAnalyticsMapper.issuedCount();
        long redeemed = couponAnalyticsMapper.redeemedCount();
        long redeemedOrderCount = couponAnalyticsMapper.redeemedOrderCount();
        long redeemedGmv = couponAnalyticsMapper.redeemedGmv();
        long discountCost = couponAnalyticsMapper.discountCost();

        CouponAnalyticsOverview o = new CouponAnalyticsOverview();
        o.setIssuedCount(issued);
        o.setRedeemedCount(redeemed);
        o.setRedemptionRate(CouponAnalyticsCalculator.redemptionRate(issued, redeemed));
        o.setRedeemedOrderCount(redeemedOrderCount);
        o.setRedeemedGmv(redeemedGmv);
        o.setDiscountCost(discountCost);
        o.setRoi(CouponAnalyticsCalculator.roi(redeemedGmv, discountCost));
        return o;
    }

    /** 券类型发放分布。 */
    public List<CouponTypeDist> typeDistribution() {
        List<Map<String, Object>> rows = couponAnalyticsMapper.typeDistribution();
        List<CouponTypeDist> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            CouponTypeDist d = new CouponTypeDist();
            d.setType((String) r.get("type"));
            d.setCount(toLong(r.get("cnt")));
            out.add(d);
        }
        return out;
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
