package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资金对账服务（PRD §F4）：每日比对「已支付订单金额」与「资金流水 PAY 入账」。
 * 差异告警（演示环境以返回差异报告形式体现）。
 */
@Service
public class ReconciliationService {

    private final PayOrderMapper payOrderMapper;
    private final FundFlowMapper fundFlowMapper;

    public ReconciliationService(PayOrderMapper payOrderMapper, FundFlowMapper fundFlowMapper) {
        this.payOrderMapper = payOrderMapper;
        this.fundFlowMapper = fundFlowMapper;
    }

    /** 对账指定自然日（默认昨日）的支付资金。 */
    public Map<String, Object> reconcile(LocalDate day) {
        if (day == null) {
            day = LocalDate.now().minusDays(1);
        }
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();

        // 期望：当日已支付订单的金额合计
        List<PayOrder> paid = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getStatus, "paid")
                .ge(PayOrder::getPaidAt, start)
                .lt(PayOrder::getPaidAt, end));
        long expected = paid.stream().mapToLong(PayOrder::getAmount).sum();

        // 实际：当日资金流水 PAY 入账合计
        List<FundFlow> flows = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                .eq(FundFlow::getType, "PAY")
                .ge(FundFlow::getCreatedAt, start)
                .lt(FundFlow::getCreatedAt, end));
        long actual = flows.stream().mapToLong(FundFlow::getAmount).sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("day", day.toString());
        result.put("expectedAmount", expected);
        result.put("actualAmount", actual);
        result.put("diff", expected - actual);
        result.put("matched", expected == actual);
        result.put("paidOrderCount", paid.size());
        return result;
    }
}
