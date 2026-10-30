package com.idlefish.trade.workorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.workorder.WorkOrderSlaPolicy;
import com.idlefish.trade.workorder.WorkOrderStateMachine;
import com.idlefish.trade.workorder.dto.WorkOrderSlaStatsVO;
import com.idlefish.trade.workorder.entity.WorkOrder;
import com.idlefish.trade.workorder.mapper.WorkOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客服工单时效 SLA 服务（F-12 补完）：超时扫描标记 + 达标率统计。
 *
 * <p>设计要点：
 * <ul>
 *   <li>阈值与判定全部下沉为纯函数 {@link WorkOrderSlaPolicy}，本服务只做「查询 → 标记 → 触达」编排；</li>
 *   <li>扫描只处理「未结且未标记」的工单，天然幂等（重复执行不会重复通知）；</li>
 *   <li>通知为 best-effort（内部 try/catch），失败绝不阻断扫描批次；</li>
 *   <li>统计为只读聚合，不影响业务数据。</li>
 * </ul>
 */
@Slf4j
@Service
public class WorkOrderSlaService {

    /** 单批扫描上限，防止大表一次拉全量。 */
    public static final int DEFAULT_SCAN_LIMIT = 200;

    private final WorkOrderMapper workOrderMapper;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;

    public WorkOrderSlaService(WorkOrderMapper workOrderMapper, NotificationService notificationService,
                               MetricsRegistry metrics) {
        this.workOrderMapper = workOrderMapper;
        this.notificationService = notificationService;
        this.metrics = metrics;
    }

    /**
     * 扫描未结工单（OPEN/IN_PROGRESS/WAITING_USER），把已越过首响或解决到期时间且尚未标记的工单置为超时，
     * 并触达发起用户与受理客服。
     *
     * @param limit 本批最多处理条数（&lt;=0 时取 {@link #DEFAULT_SCAN_LIMIT}）
     * @return 本批标记数量
     */
    public int scanBreached(int limit) {
        int size = limit <= 0 ? DEFAULT_SCAN_LIMIT : Math.min(limit, 1000);
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<WorkOrder> w = new LambdaQueryWrapper<WorkOrder>()
                .in(WorkOrder::getStatus, WorkOrderStateMachine.OPEN,
                        WorkOrderStateMachine.IN_PROGRESS, WorkOrderStateMachine.WAITING_USER)
                .and(x -> x.eq(WorkOrder::getSlaBreached, 0)
                        .and(y -> y.lt(WorkOrder::getFirstResponseDeadline, now)
                                .or().lt(WorkOrder::getResolveDeadline, now)));
        IPage<WorkOrder> page = workOrderMapper.selectPage(new Page<>(1, size), w);
        List<WorkOrder> records = page == null || page.getRecords() == null
                ? java.util.Collections.emptyList() : page.getRecords();
        int marked = 0;
        for (WorkOrder wo : records) {
            try {
                WorkOrder upd = new WorkOrder();
                upd.setId(wo.getId());
                upd.setSlaBreached(1);
                workOrderMapper.updateById(upd);
                metrics.increment("workorder.sla.breached");
                notifyBreach(wo);
                marked++;
            } catch (Exception e) {
                // 单条失败不影响整批，下轮扫描会重试（未标记成功则仍会被扫到）
                log.warn("[workorder] sla breach mark failed, ticketNo={}", wo.getTicketNo(), e);
            }
        }
        return marked;
    }

    /**
     * 达标率统计：统计近 {@code days} 天建单的工单总量、超时量、已结量与在办超时量。
     *
     * @param days 统计窗口天数（&lt;=0 时按 7 天）
     */
    public WorkOrderSlaStatsVO stats(int days) {
        int window = days <= 0 ? 7 : Math.min(days, 365);
        LocalDateTime from = LocalDateTime.now().minusDays(window);
        long total = count(q -> q.ge(WorkOrder::getCreatedAt, from));
        long breached = count(q -> q.ge(WorkOrder::getCreatedAt, from).eq(WorkOrder::getSlaBreached, 1));
        long resolved = count(q -> q.ge(WorkOrder::getCreatedAt, from)
                .in(WorkOrder::getStatus, WorkOrderStateMachine.RESOLVED, WorkOrderStateMachine.CLOSED));
        long openBreached = count(q -> q.ge(WorkOrder::getCreatedAt, from)
                .eq(WorkOrder::getSlaBreached, 1)
                .in(WorkOrder::getStatus, WorkOrderStateMachine.OPEN,
                        WorkOrderStateMachine.IN_PROGRESS, WorkOrderStateMachine.WAITING_USER));
        return new WorkOrderSlaStatsVO(window, total, breached, resolved, openBreached,
                WorkOrderSlaPolicy.complianceRate(total, breached), LocalDateTime.now());
    }

    private long count(java.util.function.Consumer<LambdaQueryWrapper<WorkOrder>> customizer) {
        LambdaQueryWrapper<WorkOrder> w = new LambdaQueryWrapper<>();
        customizer.accept(w);
        Long n = workOrderMapper.selectCount(w);
        return n == null ? 0L : n;
    }

    /** 超时触达：用户侧告知进度，客服侧（若已分配）催办，均为 best-effort。 */
    private void notifyBreach(WorkOrder wo) {
        String ticket = wo.getTicketNo();
        try {
            notificationService.notify(wo.getUserId(), NotificationType.WORKORDER_SLA_BREACHED, ticket,
                    "工单处理超时提醒",
                    "您的工单 " + ticket + " 已超出承诺处理时效，我们已加急跟进，感谢您的耐心等待");
        } catch (Exception ignored) {
        }
        if (wo.getAssigneeId() != null) {
            try {
                notificationService.notify(wo.getAssigneeId(), NotificationType.WORKORDER_SLA_BREACHED, ticket,
                        "工单时效超时", "工单 " + ticket + " 已超出 SLA 时效，请尽快处理");
            } catch (Exception ignored) {
            }
        }
    }
}
