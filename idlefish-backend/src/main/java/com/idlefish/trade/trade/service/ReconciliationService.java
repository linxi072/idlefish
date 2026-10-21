package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.enums.PayStatus;
import com.idlefish.trade.common.enums.RefundStatus;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.entity.ReconcileBill;
import com.idlefish.trade.trade.entity.ReconcileDiff;
import com.idlefish.trade.trade.entity.Refund;
import com.idlefish.trade.trade.entity.Settlement;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.trade.mapper.ReconcileBillMapper;
import com.idlefish.trade.trade.mapper.ReconcileDiffMapper;
import com.idlefish.trade.trade.mapper.RefundMapper;
import com.idlefish.trade.trade.mapper.SettlementMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资金对账服务（PRD §F4 / ITER-T02）：
 * - 旧接口 {@link #reconcile} / {@link #reconcileWithAlert}：单维 PAY 比对 + 告警（保留，向后兼容）。
 * - 新接口 {@link #reconcileAll}：日终多维对账（PAY / REFUND / SETTLE），结果持久化到
 *   t_reconcile_bill / t_reconcile_diff，差异逐笔下钻成工单（REQ-06/07/08），金额恒以「分」整型比对。
 */
@Service
public class ReconciliationService {

    private final PayOrderMapper payOrderMapper;
    private final FundFlowMapper fundFlowMapper;
    private final RefundMapper refundMapper;
    private final SettlementMapper settlementMapper;
    private final ReconcileBillMapper reconcileBillMapper;
    private final ReconcileDiffMapper reconcileDiffMapper;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;
    private final Long alertAdminUserId;

    public ReconciliationService(PayOrderMapper payOrderMapper, FundFlowMapper fundFlowMapper,
                                 RefundMapper refundMapper, SettlementMapper settlementMapper,
                                 ReconcileBillMapper reconcileBillMapper, ReconcileDiffMapper reconcileDiffMapper,
                                 NotificationService notificationService, MetricsRegistry metrics,
                                 @Value("${idlefish.notify.alert-admin-user-id:1}") Long alertAdminUserId) {
        this.payOrderMapper = payOrderMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.refundMapper = refundMapper;
        this.settlementMapper = settlementMapper;
        this.reconcileBillMapper = reconcileBillMapper;
        this.reconcileDiffMapper = reconcileDiffMapper;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.alertAdminUserId = alertAdminUserId;
    }

    /** 对账指定自然日（默认昨日）的支付资金（单维 PAY，向后兼容）。 */
    public Map<String, Object> reconcile(LocalDate day) {
        if (day == null) {
            day = LocalDate.now().minusDays(1);
        }
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();

        // 期望：当日已支付订单的金额合计
        List<PayOrder> paid = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getStatus, PayStatus.SUCCESS.getCode())
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

    /**
     * 对账并告警（单维 PAY，向后兼容）：执行 {@link #reconcile}，若差异则向管理员发送系统告警。
     */
    public Map<String, Object> reconcileWithAlert(LocalDate day) {
        Map<String, Object> report = reconcile(day);
        if (Boolean.FALSE.equals(report.get("matched"))) {
            long diff = ((Number) report.get("diff")).longValue();
            long paidOrderCount = ((Number) report.get("paidOrderCount")).longValue();
            // REQ-06：存在已支付订单却对账不平，标记支付回调超时/对账缺口
            if (paidOrderCount > 0) {
                metrics.increment("pay.callback.timeout");
            }
            notificationService.notify(alertAdminUserId, NotificationType.SYSTEM_ALERT,
                    "reconcile:" + report.get("day"), "资金对账差异",
                    "对账日 " + report.get("day") + " 期望 " + report.get("expectedAmount")
                            + " 实收 " + report.get("actualAmount") + " 差异 " + diff);
        }
        return report;
    }

    /**
     * 日终多维对账（ITER-T02 / REQ-06/07/08）：PAY / REFUND / SETTLE 三个维度分别比对平台侧与
     * 资金流水侧，结果幂等落库到 t_reconcile_bill，差异逐笔下钻成 t_reconcile_diff 工单。
     * <p>
     * 幂等性：每个 (reconcileDate, dimension) 唯一一笔账单；差异为派生数据，重跑时清后重建。
     * 人工处置粘性：账单已被处置为 RESOLVED/CLOSED 时，夜间重跑不再回退状态、不再覆盖差异明细、不再重复告警。
     *
     * @param day 对账自然日（默认昨日）
     * @return 本次生成的账单列表（每维度一条）
     */
    public List<ReconcileBill> reconcileAll(LocalDate day) {
        if (day == null) {
            day = LocalDate.now().minusDays(1);
        }
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        List<ReconcileBill> bills = new ArrayList<>();
        bills.add(reconcileDimension(ReconcileCalculator.DIM_PAY, day, start, end));
        bills.add(reconcileDimension(ReconcileCalculator.DIM_REFUND, day, start, end));
        bills.add(reconcileDimension(ReconcileCalculator.DIM_SETTLE, day, start, end));
        return bills;
    }

    /** 单维度对账：比对 → 落库账单 → 差异工单 → 告警。 */
    private ReconcileBill reconcileDimension(String dimension, LocalDate day,
                                             LocalDateTime start, LocalDateTime end) {
        Map<String, Long> expectedMap = expectedMapOf(dimension, start, end);
        Map<String, Long> actualMap = actualMapOf(dimension, start, end);

        long expectedAmount = expectedMap.values().stream().mapToLong(Long::longValue).sum();
        long actualAmount = actualMap.values().stream().mapToLong(Long::longValue).sum();
        int expectedCount = expectedMap.size();
        int actualCount = actualMap.size();
        long diffAmount = ReconcileCalculator.amountDiff(expectedAmount, actualAmount);
        boolean matched = ReconcileCalculator.matched(expectedAmount, actualAmount, expectedCount, actualCount);

        ReconcileBill existing = reconcileBillMapper.selectOne(new LambdaQueryWrapper<ReconcileBill>()
                .eq(ReconcileBill::getReconcileDate, day)
                .eq(ReconcileBill::getDimension, dimension));

        ReconcileBill bill = existing != null ? existing : new ReconcileBill();
        bill.setReconcileDate(day);
        bill.setDimension(dimension);
        bill.setExpectedCount(expectedCount);
        bill.setActualCount(actualCount);
        bill.setExpectedAmount(expectedAmount);
        bill.setActualAmount(actualAmount);
        bill.setDiffAmount(diffAmount);
        bill.setMatched(matched);
        // 保留人工处置状态（RESOLVED/CLOSED），仅首次创建时置 PENDING
        if (existing == null || existing.getStatus() == null || existing.getStatus().isBlank()) {
            bill.setStatus("PENDING");
        }

        boolean resolved = existing != null && ("RESOLVED".equals(existing.getStatus())
                || "CLOSED".equals(existing.getStatus()));

        if (!matched) {
            if (existing == null || !Boolean.TRUE.equals(existing.getAlertSent())) {
                metrics.increment("reconcile.diff");
                notificationService.notify(alertAdminUserId, NotificationType.SYSTEM_ALERT,
                        "reconcile:" + day + ":" + dimension, "资金对账差异(" + dimension + ")",
                        "对账日 " + day + " 维度 " + dimension + " 期望 " + expectedAmount
                                + " 分 实收 " + actualAmount + " 分 差异 " + diffAmount + " 分");
                bill.setAlertSent(true);
            }
            // 仅当账单仍处 PENDING（未被人工处置）时，重建差异明细工单
            if (!resolved && bill.getId() != null) {
                persistDiffs(bill.getId(), dimension,
                        ReconcileCalculator.diffByBizNo(dimension, expectedMap, actualMap));
            } else if (!resolved && existing == null) {
                // 全新账单：先插入以获得 id，再写差异明细
                reconcileBillMapper.insert(bill);
                persistDiffs(bill.getId(), dimension,
                        ReconcileCalculator.diffByBizNo(dimension, expectedMap, actualMap));
                return bill;
            }
        } else {
            bill.setAlertSent(false);
        }

        if (existing == null) {
            reconcileBillMapper.insert(bill);
        } else {
            reconcileBillMapper.updateById(bill);
        }
        return bill;
    }

    /** 差异明细幂等落库：清后重建（派生数据，重跑安全）。 */
    private void persistDiffs(Long billId, String dimension, List<ReconcileCalculator.ReconcileDiffInput> inputs) {
        reconcileDiffMapper.delete(new LambdaQueryWrapper<ReconcileDiff>()
                .eq(ReconcileDiff::getBillId, billId));
        for (ReconcileCalculator.ReconcileDiffInput in : inputs) {
            ReconcileDiff d = new ReconcileDiff();
            d.setBillId(billId);
            d.setDimension(in.dimension());
            d.setBizNo(in.bizNo());
            d.setExpectedAmount(in.expectedAmount());
            d.setActualAmount(in.actualAmount());
            d.setDiffAmount(in.diffAmount());
            d.setExpectedCount(in.expectedCount());
            d.setActualCount(in.actualCount());
            d.setStatus("OPEN");
            reconcileDiffMapper.insert(d);
        }
    }

    /** 平台侧期望金额映射（bizNo -> 金额，分），按维度选取数据源与时间字段。 */
    private Map<String, Long> expectedMapOf(String dimension, LocalDateTime start, LocalDateTime end) {
        switch (dimension) {
            case ReconcileCalculator.DIM_PAY:
                return payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                                .eq(PayOrder::getStatus, PayStatus.SUCCESS.getCode())
                                .ge(PayOrder::getPaidAt, start)
                                .lt(PayOrder::getPaidAt, end))
                        .stream().collect(Collectors.toMap(PayOrder::getOrderNo, PayOrder::getAmount, Long::sum));
            case ReconcileCalculator.DIM_REFUND:
                return refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                                .eq(Refund::getStatus, RefundStatus.REFUNDED.getCode())
                                .ge(Refund::getRefundAt, start)
                                .lt(Refund::getRefundAt, end))
                        .stream().collect(Collectors.toMap(Refund::getRefundNo, Refund::getAmount, Long::sum));
            case ReconcileCalculator.DIM_SETTLE:
                return settlementMapper.selectList(new LambdaQueryWrapper<Settlement>()
                                .eq(Settlement::getStatus, "settled")
                                .ge(Settlement::getSettleAt, start)
                                .lt(Settlement::getSettleAt, end))
                        .stream().collect(Collectors.toMap(Settlement::getSettleNo, Settlement::getAmount, Long::sum));
            default:
                return Map.of();
        }
    }

    /** 资金流水侧实际金额映射（bizNo -> 金额，分），按维度筛选 FundFlow.type。 */
    private Map<String, Long> actualMapOf(String dimension, LocalDateTime start, LocalDateTime end) {
        return fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlow>()
                        .eq(FundFlow::getType, dimension)
                        .ge(FundFlow::getCreatedAt, start)
                        .lt(FundFlow::getCreatedAt, end))
                .stream().collect(Collectors.toMap(FundFlow::getBizNo, FundFlow::getAmount, Long::sum));
    }
}
