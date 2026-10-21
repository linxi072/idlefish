package com.idlefish.trade.common.observability;

import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.TraceContext;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可观测性守卫（T01 生产化，best-effort 防御网）：周期性扫描两类资金安全异常，
 * 转化为与进程内 CAS 拦截同源的计数信号，供 {@link AlertEvaluator} 与 Prometheus 告警规则统一消费。
 * <ul>
 *   <li>REQ-06 支付回调超时/对账缺口：{@code status='pending_pay' 且 created_at} 早于此刻减去
 *       {@code stuckOrderMinutes} 的订单 = 应有关联支付流水却迟迟未支付/关单的卡单，
 *       转化为 {@code pay.callback.timeout}（每笔新卡单仅计一次，恢复后移出，避免持续虚高）；</li>
 *   <li>REQ-07 资损级重复流水：{@code t_fund_flow} 中同一 {@code biz_no} 出现多次 = 疑似重复出款/入账，
 *       转化为 {@code fund.flow.duplicate}（每笔重复 bizNo 仅计一次）。</li>
 * </ul>
 * 与 {@link com.idlefish.trade.trade.service.WithdrawalService}/
 * {@link com.idlefish.trade.trade.service.RefundService} 进程内 CAS 拦截
 * （同为 {@code fund.flow.duplicate}）、{@link com.idlefish.trade.trade.service.ReconciliationService}
 * （同为 {@code pay.callback.timeout}）形成跨进程/跨维度互补：进程内拦截覆盖并发竞态，本守卫覆盖
 * 关单失败、跨节点、历史数据等异常网。
 * <p>内存态「已上报」集合仅用于边沿触发（去重计数），重启后自动重新评估（仍在异常则继续告警，符合预期）。</p>
 */
@Component
public class ObservabilityGuardService {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityGuardService.class);

    private final OrderMapper orderMapper;
    private final FundFlowMapper fundFlowMapper;
    private final MetricsRegistry metrics;
    private final IdlefishProperties props;

    /** 已上报的卡单 orderNo（边沿触发去重）。 */
    private final Set<String> reportedStuck = ConcurrentHashMap.newKeySet();
    /** 已上报的重复 bizNo（边沿触发去重）。 */
    private final Set<String> reportedDup = ConcurrentHashMap.newKeySet();

    public ObservabilityGuardService(OrderMapper orderMapper, FundFlowMapper fundFlowMapper,
                                     MetricsRegistry metrics, IdlefishProperties props) {
        this.orderMapper = orderMapper;
        this.fundFlowMapper = fundFlowMapper;
        this.metrics = metrics;
        this.props = props;
    }

    /** 每 5 分钟扫描一次；沙箱无 MySQL 上下文时不触发，单测可直测 guard()/scan*()。 */
    @Scheduled(fixedDelay = 300_000)
    public void guard() {
        TraceContext.ensure();
        IdlefishProperties.Observability obs = props.getObservability();
        if (obs == null || !obs.isEnabled()) {
            return;
        }
        try {
            scanStuckUnpaid(obs.getStuckOrderMinutes());
            scanDuplicateFundFlow();
        } catch (Exception e) {
            log.error("[observability-guard] 扫描失败", e);
        }
    }

    /** 扫描超时未支付卡单，新卡单转化为 pay.callback.timeout。包级可测。 */
    void scanStuckUnpaid(long stuckOrderMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(stuckOrderMinutes);
        List<Order> stuck = orderMapper.listUnpaidOlderThan(cutoff);
        Set<String> currentStuck = new HashSet<>();
        for (Order o : stuck) {
            String no = o.getOrderNo();
            if (no == null) {
                continue;
            }
            currentStuck.add(no);
            if (reportedStuck.add(no)) {
                metrics.increment("pay.callback.timeout");
                log.warn("[observability-guard] 检测到超时未支付卡单 orderNo={}（createdAt 早于 {}）", no, cutoff);
            }
        }
        // 已恢复的订单移出集合，下次若再次卡住可重新计数（避免永久虚高）
        reportedStuck.retainAll(currentStuck);
    }

    /** 扫描 DB 级重复资金流水，新重复 bizNo 转化为 fund.flow.duplicate。包级可测。 */
    void scanDuplicateFundFlow() {
        List<String> dups = fundFlowMapper.listDuplicateBizNos();
        Set<String> current = new HashSet<>(dups);
        for (String bizNo : dups) {
            if (bizNo == null) {
                continue;
            }
            if (reportedDup.add(bizNo)) {
                metrics.increment("fund.flow.duplicate");
                log.warn("[observability-guard] 检测到 DB 级重复资金流水 bizNo={}", bizNo);
            }
        }
        reportedDup.retainAll(current);
    }
}
