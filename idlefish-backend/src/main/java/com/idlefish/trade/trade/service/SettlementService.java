package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.risk.entity.RiskEvent;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.Settlement;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.SettlementMapper;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算服务：交易完成后生成 T+1 结算单；到期放款并记卖家入账流水。
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final SettlementMapper settlementMapper;
    private final OrderMapper orderMapper;
    private final FundFlowMapper fundFlowMapper;
    private final RiskEventMapper riskEventMapper;
    private final FundEscrowService escrow;
    private final IdlefishProperties props;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;

    /** 平台佣金比例（PRD §4.3）。 */
    private static final double PLATFORM_RATE = 0.05;

    public SettlementService(SettlementMapper settlementMapper, OrderMapper orderMapper,
                             FundFlowMapper fundFlowMapper, RiskEventMapper riskEventMapper,
                             FundEscrowService escrow, IdlefishProperties props,
                             NotificationService notificationService, MetricsRegistry metrics) {
        this.settlementMapper = settlementMapper;
        this.orderMapper = orderMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.riskEventMapper = riskEventMapper;
        this.escrow = escrow;
        this.props = props;
        this.notificationService = notificationService;
        this.metrics = metrics;
    }

    /** 交易成功：生成待结算单（T+1）。幂等：同一订单仅生成一次，防并发双重结算资损。 */
    public void onTradeSuccess(String orderNo) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (o == null) {
            return;
        }
        // 幂等前置检查：已存在结算单直接返回
        Settlement exist = settlementMapper.selectOne(
                new LambdaQueryWrapper<Settlement>().eq(Settlement::getOrderNo, orderNo));
        if (exist != null) {
            return;
        }
        long fee = (long) (o.getPayAmount() * PLATFORM_RATE);
        long sellerAmount = o.getPayAmount() - fee;
        Settlement s = new Settlement();
        s.setSettleNo(IdGenerator.settleNo());
        s.setSellerId(o.getSellerId());
        s.setOrderNo(orderNo);
        s.setAmount(sellerAmount);
        s.setPlatformFee(fee);
        s.setStatus("pending");
        s.setSettleAt(LocalDateTime.now().plusDays(1));
        try {
            settlementMapper.insert(s);
        } catch (DuplicateKeyException e) {
            // 并发场景由 t_settlement.order_no 唯一约束兜底，安全忽略
        }
        // 真实微信分账：交易成功后即时将卖家应得款项分账至接收方（生产为卖家子商户号）；
        // 异常不阻断结算单生成（结算仍按 T+1 放款兜底）。
        try {
            metrics.timed("settle.profitshare", () -> escrow.profitShare(o.getPayNo(), o.getPayAmount(),
                    props.getPay().getMchid(), sellerAmount));
        } catch (Exception e) {
            metrics.increment("settle.profitshare.failure");
            log.warn("微信分账发起失败（不影响结算单）orderNo={}: {}", orderNo, e.getMessage());
        }
    }

    /**
     * 定时：T+1 结算到期 pending/frozen → settled，卖家入账。
     * 防资损双保险：① 以「WHERE id AND status IN(pending,frozen)」做 CAS 认领；
     * ② 资金流水 biz_no 唯一约束兜底，杜绝并发/重跑导致的重复放款。
     * 风控冻结：卖家存在未处置高危风控事件时暂缓放款（status=frozen）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void processDue() {
        List<Settlement> due = settlementMapper.selectList(new LambdaQueryWrapper<Settlement>()
                .in(Settlement::getStatus, "pending", "frozen")
                .le(Settlement::getSettleAt, LocalDateTime.now()));
        for (Settlement s : due) {
            // 风控冻结：卖家存在未处置的高危风控事件 → 冻结结算，暂缓放款
            long openRisk = riskEventMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                    .eq(RiskEvent::getUserId, s.getSellerId())
                    .eq(RiskEvent::getStatus, "open"));
            if (openRisk > 0) {
                Settlement freeze = new Settlement();
                freeze.setStatus("frozen");
                settlementMapper.update(freeze, new LambdaUpdateWrapper<Settlement>()
                        .eq(Settlement::getId, s.getId())
                        .in(Settlement::getStatus, "pending", "frozen"));
                continue;
            }

            // 无高危风控 → CAS 认领放款（pending 或 frozen 解冻后均可）
            Settlement upd = new Settlement();
            upd.setStatus("settled");
            int claimed = settlementMapper.update(upd, new LambdaUpdateWrapper<Settlement>()
                    .eq(Settlement::getId, s.getId())
                    .in(Settlement::getStatus, "pending", "frozen"));
            if (claimed == 0) {
                continue;
            }
            metrics.increment("settle.settled");

            // F-05 闭环：结算放款成功后触达卖家（资金变动通知），best-effort 不阻断主流程
            notificationService.notify(s.getSellerId(), NotificationType.SETTLEMENT_SUCCESS, s.getSettleNo(),
                    "订单已结算", "订单 " + s.getOrderNo() + " 已结算，金额 "
                    + (s.getAmount() == null ? 0 : s.getAmount() / 100.0) + " 元已打入可提现");

            FundFlow ff = new FundFlow();
            ff.setBizNo(s.getSettleNo());
            ff.setUserId(s.getSellerId());
            ff.setDirection("IN");
            ff.setAmount(s.getAmount());
            ff.setType("SETTLE");
            ff.setBalanceAfter(s.getAmount());
            try {
                fundFlowMapper.insert(ff);
            } catch (DuplicateKeyException e) {
                // biz_no 唯一约束兜底：绝不重复放款（与 CAS 认领共同构成双保险）
            }
        }
    }

    /** 后台解冻结算单（风控处置完成后，frozen → pending 重新进入放款队列）。 */
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Long id) {
        Settlement s = settlementMapper.selectById(id);
        if (s == null) {
            throw new BizException(Code.NOT_FOUND, "结算单不存在");
        }
        if (!"frozen".equals(s.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅冻结态结算单可解冻");
        }
        Settlement upd = new Settlement();
        upd.setId(id);
        upd.setStatus("pending");
        settlementMapper.updateById(upd);
    }
}
