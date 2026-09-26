package com.idlefish.trade.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.marketing.entity.Point;
import com.idlefish.trade.marketing.entity.PointLog;
import com.idlefish.trade.marketing.mapper.PointLogMapper;
import com.idlefish.trade.marketing.mapper.PointMapper;
import com.idlefish.trade.marketing.vo.PointLogVO;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 积分服务（F-13.1 积分体系）：签到 / 交易 / 评价得积分，下单积分抵现，关单释放。
 * 金额单位：分。兑换与赚取规则由 {@link IdlefishProperties.Point} 统一配置。
 * 纯计算见 {@link PointCalculator}（无副作用，单测覆盖）。
 */
@Service
public class PointService {

    public static final String BIZ_SIGNIN = "EARN_SIGNIN";
    public static final String BIZ_TRADE = "EARN_TRADE";
    public static final String BIZ_REVIEW = "EARN_REVIEW";
    public static final String BIZ_REDEEM = "REDEEM";
    public static final String BIZ_REDEEM_RELEASED = "REDEEM_RELEASED";

    private static final DateTimeFormatter FMT = com.idlefish.trade.common.util.DateTimeUtil.FMT;

    private final PointMapper pointMapper;
    private final PointLogMapper pointLogMapper;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;
    private final IdlefishProperties.Point point;

    public PointService(PointMapper pointMapper, PointLogMapper pointLogMapper,
                        NotificationService notificationService, MetricsRegistry metrics,
                        IdlefishProperties idlefishProperties) {
        this.pointMapper = pointMapper;
        this.pointLogMapper = pointLogMapper;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.point = idlefishProperties.getPoint();
    }

    /** 查询或创建用户积分账户（余额 0）。 */
    private Point getOrCreate(Long userId) {
        Point p = pointMapper.selectOne(new LambdaQueryWrapper<Point>().eq(Point::getUserId, userId));
        if (p == null) {
            p = new Point();
            p.setUserId(userId);
            p.setBalance(0L);
            p.setTotalEarned(0L);
            pointMapper.insert(p);
        }
        return p;
    }

    /** 当前积分账户（余额与累计），不存在则惰性创建返回 0 账户。 */
    public Point balance(Long userId) {
        return getOrCreate(userId);
    }

    /** 积分流水（分页，倒序）。 */
    public IPage<PointLogVO> logs(Long userId, int page, int size) {
        Page<PointLog> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        IPage<PointLog> r = pointLogMapper.selectPage(p, new LambdaQueryWrapper<PointLog>()
                .eq(PointLog::getUserId, userId).orderByDesc(PointLog::getCreatedAt));
        return r.convert(this::toLogVO);
    }

    /** 暴露积分配置（前端用于展示兑换比例 / 使用上限等）。 */
    public IdlefishProperties.Point getConfig() {
        return point;
    }

    /** 预览积分抵现金额（分），不落库；用于前端下单页实时试算。 */
    public long previewDiscount(Long userId, long usedPoint, long payableFen) {
        if (!point.isEnabled() || usedPoint <= 0) {
            return 0L;
        }
        Point p = getOrCreate(userId);
        long available = p.getBalance() == null ? 0 : p.getBalance();
        return PointCalculator.calcRedeem(usedPoint, available, payableFen, point.getRedeemPointsPerYuan(), point.getMaxRedeemRatio()).discountFen;
    }

    /** 签到：每日幂等，连续签到加成。 */
    @Transactional
    public int signin(Long userId) {
        if (!point.isEnabled()) {
            return 0;
        }
        String today = LocalDate.now().toString();
        Long todayCnt = pointLogMapper.selectCount(new LambdaQueryWrapper<PointLog>()
                .eq(PointLog::getUserId, userId).eq(PointLog::getBizType, BIZ_SIGNIN).eq(PointLog::getBizId, today));
        if (todayCnt != null && todayCnt > 0) {
            throw new BizException(Code.BIZ_ERROR, "今日已签到");
        }
        int consecutive = consecutiveDays(userId) + 1;
        int earned = PointCalculator.calcSigninPoints(point.getSigninBase(), consecutive, point.getSigninMaxBonus());
        addPoint(userId, earned, BIZ_SIGNIN, today, "每日签到+连续" + (consecutive - 1) + "天奖励");
        notifyEarned(userId, earned, "签到");
        metrics.increment("point.signin.success");
        return earned;
    }

    /** 计算截止到「昨天」的连续签到天数（回看最多 366 天，防极端死循环）。 */
    private int consecutiveDays(Long userId) {
        int days = 0;
        LocalDate d = LocalDate.now().minusDays(1);
        for (int i = 0; i < 366; i++) {
            Long c = pointLogMapper.selectCount(new LambdaQueryWrapper<PointLog>()
                    .eq(PointLog::getUserId, userId).eq(PointLog::getBizType, BIZ_SIGNIN).eq(PointLog::getBizId, d.toString()));
            if (c != null && c > 0) {
                days++;
                d = d.minusDays(1);
            } else {
                break;
            }
        }
        return days;
    }

    /** 交易完成得积分（订单支付成功时由 PayService 调用）。 */
    public long earnByTrade(Long userId, String orderNo, long payAmountFen) {
        if (!point.isEnabled()) {
            return 0L;
        }
        long earned = PointCalculator.calcTradePoints(payAmountFen, point.getEarnPointsPerYuan(), point.getTradeMaxPerOrder());
        if (earned <= 0) {
            return 0L;
        }
        addPoint(userId, earned, BIZ_TRADE, orderNo, "交易完成得积分");
        notifyEarned(userId, earned, "交易");
        metrics.increment("point.earn.trade");
        return earned;
    }

    /** 评价得积分（审核通过时由 ReviewService 调用）。 */
    public int earnByReview(Long userId, Long reviewId) {
        if (!point.isEnabled()) {
            return 0;
        }
        int earned = PointCalculator.calcReviewPoints(point.getReviewFixed());
        if (earned <= 0) {
            return 0;
        }
        addPoint(userId, earned, BIZ_REVIEW, String.valueOf(reviewId), "评价得积分");
        notifyEarned(userId, earned, "评价");
        metrics.increment("point.earn.review");
        return earned;
    }

    /**
     * 下单积分抵现（抵扣）：核验可用积分，原子扣减并写流水，返回实际抵扣金额（分）。
     * 配置关闭 / 未使用积分 / 抵扣为 0 时返回 0，不影响下单主流程。
     * 并发重复扣减（余额不足）抛 {@link Code#POINT_NOT_ENOUGH}，由下单侧决定如何处理。
     */
    @Transactional
    public long redeem(Long userId, long requestedPoint, String orderNo, long payableFen) {
        if (!point.isEnabled() || requestedPoint <= 0) {
            return 0L;
        }
        Point p = getOrCreate(userId);
        long available = p.getBalance() == null ? 0 : p.getBalance();
        PointCalculator.PointRedeemResult r = PointCalculator.calcRedeem(requestedPoint, available, payableFen,
                point.getRedeemPointsPerYuan(), point.getMaxRedeemRatio());
        if (r.usedPoints <= 0 || r.discountFen <= 0) {
            return 0L;
        }
        // 原子扣减，余额不足（并发竞争）直接报错，杜绝超兑资损
        int rows = pointMapper.update(null, new LambdaUpdateWrapper<Point>()
                .eq(Point::getUserId, userId)
                .ge(Point::getBalance, r.usedPoints)
                .setSql("balance = balance - " + r.usedPoints));
        if (rows == 0) {
            throw new BizException(Code.POINT_NOT_ENOUGH, "积分不足");
        }
        Point fresh = pointMapper.selectOne(new LambdaQueryWrapper<Point>().eq(Point::getUserId, userId));
        PointLog log = new PointLog();
        log.setUserId(userId);
        log.setBizType(BIZ_REDEEM);
        log.setBizId(orderNo);
        log.setDelta(-r.usedPoints);
        log.setBalanceAfter(fresh.getBalance());
        log.setRemark("下单积分抵扣，省" + (r.discountFen / 100.0) + "元");
        pointLogMapper.insert(log);
        metrics.increment("point.redeem.success");
        return r.discountFen;
    }

    /** 关单/取消：释放已抵扣积分（REDEEM 回滚为 REDEEM_RELEASED 加回余额）。 */
    @Transactional
    public void releaseByOrder(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return;
        }
        List<PointLog> logs = pointLogMapper.selectList(new LambdaQueryWrapper<PointLog>()
                .eq(PointLog::getBizId, orderNo).eq(PointLog::getBizType, BIZ_REDEEM));
        for (PointLog lg : logs) {
            if (lg.getDelta() == null || lg.getDelta() >= 0) {
                continue;
            }
            long back = -lg.getDelta();
            pointMapper.update(null, new LambdaUpdateWrapper<Point>()
                    .eq(Point::getUserId, lg.getUserId())
                    .setSql("balance = balance + " + back));
            Point fresh = pointMapper.selectOne(new LambdaQueryWrapper<Point>().eq(Point::getUserId, lg.getUserId()));
            PointLog rel = new PointLog();
            rel.setUserId(lg.getUserId());
            rel.setBizType(BIZ_REDEEM_RELEASED);
            rel.setBizId(orderNo);
            rel.setDelta(back);
            rel.setBalanceAfter(fresh.getBalance());
            rel.setRemark("关单释放积分抵扣");
            pointLogMapper.insert(rel);
        }
    }

    /** 内部：增加积分（原子），写流水。delta 正=获得，负=抵扣（仅减少余额，不扣累计）。 */
    @Transactional
    void addPoint(Long userId, long delta, String bizType, String bizId, String remark) {
        Point p = getOrCreate(userId);
        String sql = delta >= 0
                ? "balance = balance + " + delta + ", total_earned = total_earned + " + delta
                : "balance = balance + " + delta;
        pointMapper.update(null, new LambdaUpdateWrapper<Point>().eq(Point::getId, p.getId()).setSql(sql));
        Point fresh = pointMapper.selectOne(new LambdaQueryWrapper<Point>().eq(Point::getUserId, userId));
        PointLog log = new PointLog();
        log.setUserId(userId);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setDelta(delta);
        log.setBalanceAfter(fresh.getBalance());
        log.setRemark(remark);
        pointLogMapper.insert(log);
    }

    private void notifyEarned(Long userId, long earned, String scene) {
        try {
            notificationService.notify(userId, NotificationType.POINT_EARNED, String.valueOf(userId),
                    "积分到账", "您通过" + scene + "获得 " + earned + " 积分");
        } catch (Exception ignored) {
            // 通知 best-effort，不影响积分主流程
        }
    }

    private PointLogVO toLogVO(PointLog l) {
        PointLogVO vo = new PointLogVO();
        vo.setId(l.getId());
        vo.setUserId(l.getUserId());
        vo.setBizType(l.getBizType());
        vo.setBizId(l.getBizId());
        vo.setDelta(l.getDelta());
        vo.setBalanceAfter(l.getBalanceAfter());
        vo.setRemark(l.getRemark());
        vo.setCreatedAt(l.getCreatedAt() == null ? null : l.getCreatedAt().format(FMT));
        return vo;
    }
}
