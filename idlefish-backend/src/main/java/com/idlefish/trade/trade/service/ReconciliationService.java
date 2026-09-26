package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资金对账服务（PRD §F4）：每日比对「已支付订单金额」与「资金流水 PAY 入账」。
 * 差异时通过 {@link NotificationService} 向管理员发送系统告警（best-effort，不阻塞主流程）。
 */
@Service
public class ReconciliationService {

    private final PayOrderMapper payOrderMapper;
    private final FundFlowMapper fundFlowMapper;
    private final NotificationService notificationService;
    private final Long alertAdminUserId;

    public ReconciliationService(PayOrderMapper payOrderMapper, FundFlowMapper fundFlowMapper,
                                 NotificationService notificationService,
                                 @Value("${idlefish.notify.alert-admin-user-id:1}") Long alertAdminUserId) {
        this.payOrderMapper = payOrderMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.notificationService = notificationService;
        this.alertAdminUserId = alertAdminUserId;
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

    /**
     * 对账并告警：执行 {@link #reconcile}，若差异（matched=false）则向管理员发送系统告警。
     * 告警为 best-effort —— 任一渠道异常不影响对账结果返回。
     */
    public Map<String, Object> reconcileWithAlert(LocalDate day) {
        Map<String, Object> report = reconcile(day);
        if (Boolean.FALSE.equals(report.get("matched"))) {
            long diff = ((Number) report.get("diff")).longValue();
            notificationService.notify(alertAdminUserId, NotificationType.SYSTEM_ALERT,
                    "reconcile:" + report.get("day"), "资金对账差异",
                    "对账日 " + report.get("day") + " 期望 " + report.get("expectedAmount")
                            + " 实收 " + report.get("actualAmount") + " 差异 " + diff);
        }
        return report;
    }
}
