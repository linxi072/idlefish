package com.idlefish.trade.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.enums.CreditLevel;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.ReviewMapper;
import com.idlefish.trade.user.entity.CreditLog;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.CreditLogMapper;
import com.idlefish.trade.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 信用服务（F-06）：基于多维因子从零重算用户信用分，并落信用变动日志。
 * 重算为幂等操作，由履约/评价/封禁等事件触发，保证信用分与业务事实一致。
 */
@Service
public class CreditService {

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final ReviewMapper reviewMapper;
    private final RiskEventMapper riskEventMapper;
    private final CreditLogMapper creditLogMapper;
    private final CreditCalculator calculator;

    public CreditService(UserMapper userMapper, OrderMapper orderMapper, ReviewMapper reviewMapper,
                         RiskEventMapper riskEventMapper, CreditLogMapper creditLogMapper,
                         IdlefishProperties props) {
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
        this.reviewMapper = reviewMapper;
        this.riskEventMapper = riskEventMapper;
        this.creditLogMapper = creditLogMapper;
        IdlefishProperties.Credit c = props.getCredit();
        this.calculator = new CreditCalculator(new CreditCalculator.CreditWeights(
                c.getRealName(), c.getAgePer30d(), c.getAgeCap(), c.getFulfillment(),
                c.getReview(), c.getReviewBaseline(), c.getBanPenalty(), c.getRiskPerEvent(), c.getRiskCap()));
    }

    /** 从业务事实重算并写回用户信用分 + 变动日志。返回最新信用分。 */
    @Transactional
    public int recompute(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            return 0;
        }
        int oldScore = u.getCreditScore() == null ? 0 : u.getCreditScore();
        int newScore = calculator.compute(buildFactors(u));

        User upd = new User();
        upd.setId(userId);
        upd.setCreditScore(newScore);
        userMapper.updateById(upd);

        CreditLog log = new CreditLog();
        log.setUserId(userId);
        log.setDelta(newScore - oldScore);
        log.setReason("recompute");
        log.setSnapshot(newScore);
        creditLogMapper.insert(log);
        return newScore;
    }

    /** 读取已存储信用分对应的等级（不触发重算）。 */
    public CreditLevel levelOf(Long userId) {
        User u = userMapper.selectById(userId);
        int score = u != null && u.getCreditScore() != null ? u.getCreditScore() : 0;
        return CreditLevel.fromScore(score);
    }

    private CreditCalculator.CreditFactors buildFactors(User u) {
        CreditCalculator.CreditFactors f = new CreditCalculator.CreditFactors();
        f.setRealNameVerified(u.getRealNameVerified() != null && u.getRealNameVerified() == 1);
        f.setBanned(u.getStatus() != null && u.getStatus() == 1);
        if (u.getCreatedAt() != null) {
            f.setAccountAgeDays((int) Math.max(0, ChronoUnit.DAYS.between(u.getCreatedAt().toLocalDate(), LocalDate.now())));
        }
        Long id = u.getId();
        f.setCompletedOrders(countOrders(id, java.util.List.of("COMPLETED", "CLOSED")));
        f.setTotalOrders(countOrders(id, java.util.List.of("PAID", "SHIPPING", "COMPLETED", "CLOSED")));
        f.setGoodReviews((int) reviewMapper.selectCount(new LambdaQueryWrapper<com.idlefish.trade.trade.entity.Review>()
                .eq(com.idlefish.trade.trade.entity.Review::getTargetId, id)
                .eq(com.idlefish.trade.trade.entity.Review::getStatus, 1)
                .ge(com.idlefish.trade.trade.entity.Review::getRating, 4)).intValue());
        f.setTotalReviews((int) reviewMapper.selectCount(new LambdaQueryWrapper<com.idlefish.trade.trade.entity.Review>()
                .eq(com.idlefish.trade.trade.entity.Review::getTargetId, id)
                .eq(com.idlefish.trade.trade.entity.Review::getStatus, 1)).intValue());
        f.setHighRiskCount((int) riskEventMapper.selectCount(new LambdaQueryWrapper<com.idlefish.trade.risk.entity.RiskEvent>()
                .eq(com.idlefish.trade.risk.entity.RiskEvent::getUserId, id)
                .eq(com.idlefish.trade.risk.entity.RiskEvent::getLevel, "high")).intValue());
        return f;
    }

    private int countOrders(Long userId, java.util.List<String> statuses) {
        return (int) orderMapper.selectCount(new LambdaQueryWrapper<com.idlefish.trade.trade.entity.Order>()
                .and(w -> w.eq(com.idlefish.trade.trade.entity.Order::getBuyerId, userId)
                        .or().eq(com.idlefish.trade.trade.entity.Order::getSellerId, userId))
                .in(com.idlefish.trade.trade.entity.Order::getStatus, statuses)).intValue();
    }
}
