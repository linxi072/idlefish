package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.Settlement;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.SettlementMapper;
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

    private final SettlementMapper settlementMapper;
    private final OrderMapper orderMapper;
    private final FundFlowMapper fundFlowMapper;

    /** 平台佣金比例（PRD §4.3）。 */
    private static final double PLATFORM_RATE = 0.05;

    public SettlementService(SettlementMapper settlementMapper, OrderMapper orderMapper, FundFlowMapper fundFlowMapper) {
        this.settlementMapper = settlementMapper;
        this.orderMapper = orderMapper;
        this.fundFlowMapper = fundFlowMapper;
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
    }

    /**
     * 定时：T+1 结算到期 pending → settled，卖家入账。
     * 防资损双保险：① 以「WHERE id AND status='pending'」做 CAS 认领，仅一个线程能置为 settled；
     * ② 资金流水 biz_no 唯一约束兜底，杜绝并发/重跑导致的重复放款。
     */
    @Transactional(rollbackFor = Exception.class)
    public void processDue() {
        List<Settlement> due = settlementMapper.selectList(new LambdaQueryWrapper<Settlement>()
                .eq(Settlement::getStatus, "pending")
                .le(Settlement::getSettleAt, LocalDateTime.now()));
        for (Settlement s : due) {
            // CAS 认领：仅当仍处于 pending 时置为 settled，影响行数为 0 说明已被其它线程/上次执行认领
            Settlement upd = new Settlement();
            upd.setStatus("settled");
            int claimed = settlementMapper.update(upd, new LambdaUpdateWrapper<Settlement>()
                    .eq(Settlement::getId, s.getId())
                    .eq(Settlement::getStatus, "pending"));
            if (claimed == 0) {
                continue;
            }

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
}
