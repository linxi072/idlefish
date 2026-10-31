package com.idlefish.trade.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.marketing.entity.Coupon;
import com.idlefish.trade.marketing.entity.UserCoupon;
import com.idlefish.trade.marketing.mapper.CouponMapper;
import com.idlefish.trade.marketing.mapper.UserCouponMapper;
import com.idlefish.trade.marketing.vo.CouponVO;
import com.idlefish.trade.marketing.vo.UserCouponVO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 优惠券数据访问（F-10）：领券中心 / 我的券 / 可用券 / 释放 的只读查询与批量加载。
 * 由 {@link CouponService} 在构造器内持有，承载 Mapper 交互，纯函数映射下沉至 {@link CouponViews}。
 */
class CouponStore {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final ItemMapper itemMapper;

    CouponStore(CouponMapper couponMapper, UserCouponMapper userCouponMapper, ItemMapper itemMapper) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.itemMapper = itemMapper;
    }

    /**
     * 领券中心：可领券分页（已抢光/已结束/未开始不展示）。
     * 分页与「未抢光」过滤（claimed_count < total_count）均下沉至 DB，
     * 避免全表加载到内存再做内存分页；列间比较经 apply 片段表达。
     */
    IPage<CouponVO> pageCenter(int page, int size, Long userId) {
        LambdaQueryWrapper<Coupon> w = new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, CouponService.STATUS_ACTIVE)
                .gt(Coupon::getEndAt, LocalDateTime.now())
                .apply("claimed_count < total_count")
                .orderByDesc(Coupon::getCreatedAt);
        IPage<Coupon> pageResult = couponMapper.selectPage(new Page<>(page, size), w);
        List<Coupon> slice = pageResult.getRecords();
        // 批量查询当前用户已领券（一次 IN 查询替代逐券 selectCount 的 N+1）
        Set<Long> claimedIds = new HashSet<>();
        if (userId != null && !slice.isEmpty()) {
            List<UserCoupon> owned = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getUserId, userId)
                    .in(UserCoupon::getCouponId, slice.stream().map(Coupon::getId).collect(Collectors.toSet())));
            owned.forEach(uc -> claimedIds.add(uc.getCouponId()));
        }
        List<CouponVO> vos = slice.stream()
                .map(c -> CouponViews.toCenterVO(c, userId, claimedIds)).collect(Collectors.toList());
        IPage<CouponVO> result = new Page<>(page, size, pageResult.getTotal());
        result.setRecords(vos);
        return result;
    }

    /** 我的优惠券（status 可选：UNUSED/USED/EXPIRED/空=全部）。 */
    List<UserCouponVO> myCoupons(Long userId, String status) {
        LambdaQueryWrapper<UserCoupon> w = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getCreatedAt);
        if (status != null && !status.isBlank()) {
            w.eq(UserCoupon::getStatus, status);
        }
        List<UserCoupon> list = userCouponMapper.selectList(w);
        // 批量加载券模板，避免逐张 selectById 的 N+1
        Map<Long, Coupon> couponMap = batchCoupons(list.stream()
                .map(UserCoupon::getCouponId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return list.stream().map(uc -> CouponViews.toUserVO(uc, couponMap)).collect(Collectors.toList());
    }

    /** 下单可用券：未使用 + 未过期 + 适用该商品 + 满足门槛；附可抵扣金额。 */
    List<UserCouponVO> availableCoupons(Long userId, Long itemId, long goodsAmount) {
        List<UserCoupon> list = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, CouponService.UC_UNUSED));
        Item item = itemMapper.selectById(itemId);
        LocalDateTime now = LocalDateTime.now();
        // 批量加载券模板，避免逐张 selectById 的 N+1
        Map<Long, Coupon> couponMap = batchCoupons(list.stream()
                .map(UserCoupon::getCouponId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<UserCouponVO> result = new ArrayList<>();
        for (UserCoupon uc : list) {
            if (uc.getExpireAt() != null && now.isAfter(uc.getExpireAt())) {
                continue;
            }
            Coupon coupon = couponMap.get(uc.getCouponId());
            if (coupon == null || !CouponViews.isScopeApplicable(coupon, item)) {
                continue;
            }
            UserCouponVO vo = CouponViews.toUserVO(uc, couponMap);
            vo.setDiscountAmount(CouponViews.calculateDiscount(coupon, goodsAmount));
            result.add(vo);
        }
        // 按可抵扣金额降序，优先推荐更划算的券
        result.sort((a, b) -> Long.compare(b.getDiscountAmount() == null ? 0 : b.getDiscountAmount(),
                a.getDiscountAmount() == null ? 0 : a.getDiscountAmount()));
        return result;
    }

    /** 订单取消/关单时释放优惠券（USED → UNUSED，解绑订单）。 */
    void releaseByOrder(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return;
        }
        List<UserCoupon> list = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getOrderNo, orderNo).eq(UserCoupon::getStatus, CouponService.UC_USED));
        for (UserCoupon uc : list) {
            uc.setStatus(CouponService.UC_UNUSED);
            uc.setOrderNo(null);
            uc.setUsedAt(null);
            userCouponMapper.updateById(uc);
        }
    }

    /** 批量加载券模板（一次 IN 查询替代逐张 selectById 的 N+1）。 */
    Map<Long, Coupon> batchCoupons(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return couponMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c, (a, b) -> a));
    }
}
