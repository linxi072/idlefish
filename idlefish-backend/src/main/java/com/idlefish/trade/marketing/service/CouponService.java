package com.idlefish.trade.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import org.springframework.dao.DuplicateKeyException;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.marketing.dto.CouponCreateDTO;
import com.idlefish.trade.marketing.entity.Coupon;
import com.idlefish.trade.marketing.entity.UserCoupon;
import com.idlefish.trade.marketing.mapper.CouponMapper;
import com.idlefish.trade.marketing.mapper.UserCouponMapper;
import com.idlefish.trade.marketing.vo.CouponVO;
import com.idlefish.trade.marketing.vo.UserCouponVO;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.notify.service.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券服务（F-10 营销）：领取 / 发放 / 核销 / 下单可用计算。
 * 纯函数（折扣计算 / 适用范围 / VO 映射）下沉至 {@link CouponViews}，
 * 只读查询与批量加载下沉至 {@link CouponStore}，本类聚焦写操作与编排。
 * 金额单位：分。公开 API 与构造签名保持向后兼容。
 */
@Service
public class CouponService {

    // 券类型
    public static final String TYPE_FULL_REDUCTION = "FULL_REDUCTION";
    public static final String TYPE_NO_THRESHOLD = "NO_THRESHOLD";
    public static final String TYPE_DISCOUNT = "DISCOUNT";
    // 适用范围
    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_CATEGORY = "CATEGORY";
    public static final String SCOPE_ITEM = "ITEM";
    // 券状态
    public static final String STATUS_ACTIVE = "ACTIVE";
    // 用户券状态
    public static final String UC_UNUSED = "UNUSED";
    public static final String UC_USED = "USED";
    public static final String UC_EXPIRED = "EXPIRED";

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final ItemMapper itemMapper;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;
    private final CouponStore store;

    public CouponService(CouponMapper couponMapper, UserCouponMapper userCouponMapper,
                         ItemMapper itemMapper, NotificationService notificationService,
                         MetricsRegistry metrics) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.itemMapper = itemMapper;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.store = new CouponStore(couponMapper, userCouponMapper, itemMapper);
    }

    /**
     * 纯函数：计算某券对商品金额（分）的可抵扣金额。委托 {@link CouponViews#calculateDiscount}。
     */
    public long calculateDiscount(Coupon c, long goodsAmount) {
        return CouponViews.calculateDiscount(c, goodsAmount);
    }

    /** 领券中心：可领券分页（已抢光/已结束/未开始不展示）。 */
    public IPage<CouponVO> pageCenter(int page, int size, Long userId) {
        return store.pageCenter(page, size, userId);
    }

    /** 领取优惠券（幂等 + 库存 + 限领 + 时效 + 活动状态）。 */
    public UserCoupon claim(Long userId, Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BizException(Code.BIZ_ERROR, "优惠券不存在");
        }
        if (!STATUS_ACTIVE.equals(coupon.getStatus())) {
            throw new BizException(Code.BIZ_ERROR, "优惠券不在发放中");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
            throw new BizException(Code.BIZ_ERROR, "优惠券不在领取时间范围内");
        }
        int limit = coupon.getPerUserLimit() == null ? 1 : coupon.getPerUserLimit();
        long owned = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId).eq(UserCoupon::getCouponId, couponId));
        if (owned >= limit) {
            throw new BizException(Code.BIZ_ERROR, "您已领取，不可重复领取");
        }
        // 原子扣减库存（claimedCount < totalCount 才 +1）。用 setSql 避免增量表达式的列名解析依赖
        int rows = couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, couponId)
                .eq(Coupon::getStatus, STATUS_ACTIVE)
                .lt(Coupon::getClaimedCount, coupon.getTotalCount())
                .setSql("claimed_count = claimed_count + 1"));
        if (rows == 0) {
            throw new BizException(Code.COUPON_SOLD_OUT, "手慢了，优惠券已抢光");
        }
        UserCoupon uc = new UserCoupon();
        uc.setCouponId(couponId);
        uc.setUserId(userId);
        uc.setStatus(UC_UNUSED);
        uc.setExpireAt(coupon.getEndAt());
        uc.setClaimedAt(now);
        try {
            userCouponMapper.insert(uc);
        } catch (DuplicateKeyException e) {
            // 并发重复领取兜底：唯一索引拦截后，回滚本次已占用的库存计数，返回友好错误（避免 500 资损/误报）
            couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                    .eq(Coupon::getId, couponId)
                    .setSql("claimed_count = claimed_count - 1"));
            throw new BizException(Code.BIZ_ERROR, "您已领取，不可重复领取");
        }

        // 领取成功通知（best-effort，不影响领取主流程）
        try {
            notificationService.notify(userId, NotificationType.COUPON_CLAIMED, String.valueOf(couponId),
                    "优惠券领取成功", "您已领取「" + coupon.getName() + "」，下单时可抵扣");
        } catch (Exception ignored) {
        }
        metrics.increment("coupon.claim.success");
        return uc;
    }

    /** 我的优惠券（status 可选：UNUSED/USED/EXPIRED/空=全部）。 */
    public List<UserCouponVO> myCoupons(Long userId, String status) {
        return store.myCoupons(userId, status);
    }

    /** 下单可用券：未使用 + 未过期 + 适用该商品 + 满足门槛；附可抵扣金额。 */
    public List<UserCouponVO> availableCoupons(Long userId, Long itemId, long goodsAmount) {
        return store.availableCoupons(userId, itemId, goodsAmount);
    }

    /**
     * 核销优惠券（下单时调用）。校验归属/状态/时效/适用范围/门槛，标记 USED 并绑定订单号。
     * @return 实际抵扣金额（分）
     */
    public long redeem(Long userId, Long userCouponId, String orderNo, Long itemId, long goodsAmount) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !userId.equals(uc.getUserId())) {
            throw new BizException(Code.BIZ_ERROR, "优惠券不存在");
        }
        if (!UC_UNUSED.equals(uc.getStatus())) {
            throw new BizException(Code.BIZ_ERROR, "优惠券已使用或不可用");
        }
        if (uc.getExpireAt() != null && LocalDateTime.now().isAfter(uc.getExpireAt())) {
            uc.setStatus(UC_EXPIRED);
            userCouponMapper.updateById(uc);
            throw new BizException(Code.BIZ_ERROR, "优惠券已过期");
        }
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BizException(Code.BIZ_ERROR, "优惠券模板不存在");
        }
        Item item = itemMapper.selectById(itemId);
        if (!CouponViews.isScopeApplicable(coupon, item)) {
            throw new BizException(Code.BIZ_ERROR, "优惠券不适用于该商品");
        }
        long discount = CouponViews.calculateDiscount(coupon, goodsAmount);
        if (discount <= 0) {
            throw new BizException(Code.BIZ_ERROR, "订单金额不满足优惠券使用门槛");
        }
        uc.setStatus(UC_USED);
        uc.setOrderNo(orderNo);
        uc.setUsedAt(LocalDateTime.now());
        uc.setDiscountAmount(discount);
        userCouponMapper.updateById(uc);
        metrics.increment("coupon.redeem.success");
        return discount;
    }

    /** 订单取消/关单时释放优惠券（USED → UNUSED，解绑订单）。 */
    public void releaseByOrder(String orderNo) {
        store.releaseByOrder(orderNo);
    }

    /** PC 运营后台：创建优惠券（默认 ACTIVE 发放中）。 */
    public Coupon createCoupon(CouponCreateDTO dto) {
        Coupon c = new Coupon();
        c.setName(dto.getName());
        c.setType(dto.getType());
        c.setThresholdAmount(dto.getThresholdAmount() == null ? 0 : dto.getThresholdAmount());
        c.setReduceAmount(dto.getReduceAmount() == null ? 0 : dto.getReduceAmount());
        c.setDiscountRate(dto.getDiscountRate() == null ? 1.0 : dto.getDiscountRate());
        c.setMaxDiscountAmount(dto.getMaxDiscountAmount() == null ? 0 : dto.getMaxDiscountAmount());
        c.setScope(dto.getScope() == null ? SCOPE_ALL : dto.getScope());
        c.setScopeId(dto.getScopeId());
        c.setTotalCount(dto.getTotalCount() == null ? 0 : dto.getTotalCount());
        c.setClaimedCount(0);
        c.setPerUserLimit(dto.getPerUserLimit() == null ? 1 : dto.getPerUserLimit());
        c.setStatus(STATUS_ACTIVE);
        c.setStartAt(dto.getStartAt());
        c.setEndAt(dto.getEndAt());
        couponMapper.insert(c);
        return c;
    }

    /** PC 运营后台：启停 / 结束优惠券。 */
    public void updateStatus(Long couponId, String status) {
        Coupon c = couponMapper.selectById(couponId);
        if (c == null) {
            throw new BizException(Code.BIZ_ERROR, "优惠券不存在");
        }
        Coupon upd = new Coupon();
        upd.setId(couponId);
        upd.setStatus(status);
        couponMapper.updateById(upd);
    }

    /** 直接发放券给用户（邀请奖励 / 运营定向发放，不经过领券中心）。 */
    public void grantCoupon(Long userId, Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BizException(Code.BIZ_ERROR, "奖励券模板不存在");
        }
        UserCoupon uc = new UserCoupon();
        uc.setCouponId(couponId);
        uc.setUserId(userId);
        uc.setStatus(UC_UNUSED);
        uc.setExpireAt(coupon.getEndAt());
        uc.setClaimedAt(LocalDateTime.now());
        userCouponMapper.insert(uc);
    }
}
