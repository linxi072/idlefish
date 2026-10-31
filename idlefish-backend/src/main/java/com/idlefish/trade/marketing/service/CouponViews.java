package com.idlefish.trade.marketing.service;

import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.marketing.entity.Coupon;
import com.idlefish.trade.marketing.entity.UserCoupon;
import com.idlefish.trade.marketing.vo.CouponVO;
import com.idlefish.trade.marketing.vo.UserCouponVO;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 优惠券纯函数映射与计算（F-10）：折扣计算 / 适用范围 / VO 映射。
 * 无副作用、无 Mapper 依赖，便于离线单测。
 */
public final class CouponViews {

    private CouponViews() {
    }

    /**
     * 纯函数：计算某券对商品金额（分）的可抵扣金额。
     * - 满减：满足门槛返回减免，否则 0
     * - 无门槛：直接返回减免（封顶不超过商品金额）
     * - 折扣：商品金额 * (1-rate)，超封顶取封顶；折扣后不低于 0
     * 返回 0 表示该券当前不可用（门槛未达 / 类型不匹配）。
     */
    public static long calculateDiscount(Coupon c, long goodsAmount) {
        if (c == null || goodsAmount <= 0) {
            return 0L;
        }
        long discount;
        switch (c.getType()) {
            case CouponService.TYPE_FULL_REDUCTION:
                long threshold = c.getThresholdAmount() == null ? 0 : c.getThresholdAmount();
                if (goodsAmount < threshold) {
                    return 0L;
                }
                discount = c.getReduceAmount() == null ? 0 : c.getReduceAmount();
                break;
            case CouponService.TYPE_NO_THRESHOLD:
                discount = c.getReduceAmount() == null ? 0 : c.getReduceAmount();
                break;
            case CouponService.TYPE_DISCOUNT:
                double rate = c.getDiscountRate() == null ? 1.0 : c.getDiscountRate();
                discount = Math.round(goodsAmount * (1.0 - rate));
                long cap = c.getMaxDiscountAmount() == null ? 0 : c.getMaxDiscountAmount();
                if (cap > 0 && discount > cap) {
                    discount = cap;
                }
                break;
            default:
                return 0L;
        }
        if (discount < 0) {
            discount = 0;
        }
        // 抵扣不得超过商品金额
        return Math.min(discount, goodsAmount);
    }

    /** 适用范围校验：ALL 恒成立；CATEGORY 命中商品类目；ITEM 命中商品。 */
    public static boolean isScopeApplicable(Coupon coupon, Item item) {
        if (CouponService.SCOPE_ALL.equals(coupon.getScope())) {
            return true;
        }
        if (item == null) {
            return false;
        }
        if (CouponService.SCOPE_CATEGORY.equals(coupon.getScope())) {
            return coupon.getScopeId() != null && coupon.getScopeId().equals(item.getCategoryId());
        }
        if (CouponService.SCOPE_ITEM.equals(coupon.getScope())) {
            return coupon.getScopeId() != null && coupon.getScopeId().equals(item.getId());
        }
        return false;
    }

    public static CouponVO toCenterVO(Coupon c, Long userId, Set<Long> claimedIds) {
        CouponVO vo = new CouponVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setType(c.getType());
        vo.setThresholdAmount(c.getThresholdAmount());
        vo.setReduceAmount(c.getReduceAmount());
        vo.setDiscountRate(c.getDiscountRate());
        vo.setMaxDiscountAmount(c.getMaxDiscountAmount());
        vo.setScope(c.getScope());
        vo.setScopeId(c.getScopeId());
        vo.setTotalCount(c.getTotalCount());
        vo.setClaimedCount(c.getClaimedCount());
        vo.setPerUserLimit(c.getPerUserLimit());
        vo.setStatus(c.getStatus());
        vo.setStartAt(c.getStartAt());
        vo.setEndAt(c.getEndAt());
        vo.setClaimed(userId != null && claimedIds.contains(c.getId()));
        vo.setClaimable(c.getStatus() != null && c.getStatus().equals(CouponService.STATUS_ACTIVE)
                && c.getEndAt() != null && LocalDateTime.now().isBefore(c.getEndAt())
                && (c.getClaimedCount() == null ? 0 : c.getClaimedCount()) < (c.getTotalCount() == null ? 0 : c.getTotalCount()));
        return vo;
    }

    public static UserCouponVO toUserVO(UserCoupon uc, Map<Long, Coupon> couponMap) {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(uc.getId());
        vo.setCouponId(uc.getCouponId());
        vo.setStatus(uc.getStatus());
        vo.setOrderNo(uc.getOrderNo());
        vo.setExpireAt(uc.getExpireAt());
        Coupon c = couponMap.get(uc.getCouponId());
        if (c != null) {
            vo.setName(c.getName());
            vo.setType(c.getType());
            vo.setThresholdAmount(c.getThresholdAmount());
            vo.setReduceAmount(c.getReduceAmount());
            vo.setDiscountRate(c.getDiscountRate());
            vo.setMaxDiscountAmount(c.getMaxDiscountAmount());
            vo.setScope(c.getScope());
            vo.setScopeId(c.getScopeId());
        }
        return vo;
    }
}
