package com.idlefish.trade.workorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.dispute.DisputeStateMachine;
import com.idlefish.trade.dispute.entity.Dispute;
import com.idlefish.trade.dispute.mapper.DisputeMapper;
import com.idlefish.trade.event.DomainEventPublisher;
import com.idlefish.trade.event.EventType;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.workorder.WorkOrderDisputeLink;
import com.idlefish.trade.workorder.WorkOrderSlaPolicy;
import com.idlefish.trade.workorder.WorkOrderStateMachine;
import com.idlefish.trade.workorder.dto.WorkOrderLinkedDisputeVO;
import com.idlefish.trade.workorder.entity.WorkOrder;
import com.idlefish.trade.workorder.mapper.WorkOrderMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 客服工单服务（F-12）：提交 / 分配 / 流转（处理/待补充/解决）/ 用户关闭（含满意度回访）/ 查询。
 *
 * <p>与维权纠纷（dispute）通过 {@code disputeNo} 单向联通：本服务只承载「通用客服流转」，不处理资金裁决；
 * 维权单可「转人工」派生工单（{@link #createFromDispute}），映射规则见 {@link WorkOrderDisputeLink}。
 * 通知走 F-02 通知中心，均为 best-effort（内部 try/catch 不阻断主流程）；每次状态变更均发布领域事件
 * 供下游（风控/数据/通知）消费。时效 SLA 见 {@link WorkOrderSlaPolicy}，扫描与统计见 {@link WorkOrderSlaService}。
 */
@Service
public class WorkOrderService {

    // 工单类型
    public static final String TYPE_GENERAL = "GENERAL";
    public static final String TYPE_REFUND = "REFUND";
    public static final String TYPE_TECHNICAL = "TECHNICAL";
    public static final String TYPE_COMPLAINT = "COMPLAINT";
    public static final String TYPE_SUGGESTION = "SUGGESTION";
    // 优先级
    public static final String PRIORITY_LOW = "LOW";
    public static final String PRIORITY_NORMAL = "NORMAL";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_URGENT = "URGENT";
    // 渠道
    public static final String CHANNEL_APP = "APP";
    public static final String CHANNEL_WEB = "WEB";
    public static final String CHANNEL_MINI = "MINI";
    public static final String CHANNEL_PHONE = "PHONE";

    private static final Set<String> VALID_TYPES = new HashSet<>(Arrays.asList(
            TYPE_GENERAL, TYPE_REFUND, TYPE_TECHNICAL, TYPE_COMPLAINT, TYPE_SUGGESTION));
    private static final Set<String> VALID_PRIORITIES = new HashSet<>(Arrays.asList(
            PRIORITY_LOW, PRIORITY_NORMAL, PRIORITY_HIGH, PRIORITY_URGENT));
    private static final Set<String> VALID_CHANNELS = new HashSet<>(Arrays.asList(
            CHANNEL_APP, CHANNEL_WEB, CHANNEL_MINI, CHANNEL_PHONE));

    private final WorkOrderMapper workOrderMapper;
    private final DisputeMapper disputeMapper;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;
    private final DomainEventPublisher eventPublisher;

    public WorkOrderService(WorkOrderMapper workOrderMapper, DisputeMapper disputeMapper,
                            NotificationService notificationService, MetricsRegistry metrics,
                            DomainEventPublisher eventPublisher) {
        this.workOrderMapper = workOrderMapper;
        this.disputeMapper = disputeMapper;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 用户提交客服工单（无维权关联）。校验类型/优先级/渠道合法性，落库 OPEN 状态并发布 WORKORDER_OPENED 事件。
     *
     * @return 工单 ID
     */
    public Long create(Long userId, String type, String priority, String channel,
                       String subject, String description, String orderNo) {
        return create(userId, type, priority, channel, subject, description, orderNo, null);
    }

    /**
     * 用户提交客服工单，可携带维权单号（维权转人工时由 {@link #createFromDispute} 派生，也可直接调用）。
     * 校验类型/优先级/渠道合法性，按优先级落 SLA 到期时间，落库 OPEN 状态并发布 WORKORDER_OPENED 事件。
     *
     * @param disputeNo 关联维权工单号（可空）
     * @return 工单 ID
     */
    public Long create(Long userId, String type, String priority, String channel,
                       String subject, String description, String orderNo, String disputeNo) {
        if (subject == null || subject.isBlank()) {
            throw new BizException(Code.PARAM_MISSING, "工单标题不能为空");
        }
        if (description == null || description.isBlank()) {
            throw new BizException(Code.PARAM_MISSING, "问题描述不能为空");
        }
        if (!VALID_TYPES.contains(type)) {
            throw new BizException(Code.WORKORDER_TYPE_INVALID, "工单类型不合法");
        }
        if (!VALID_PRIORITIES.contains(priority)) {
            throw new BizException(Code.WORKORDER_PRIORITY_INVALID, "工单优先级不合法");
        }
        if (!VALID_CHANNELS.contains(channel)) {
            throw new BizException(Code.WORKORDER_CHANNEL_INVALID, "工单来源渠道不合法");
        }

        LocalDateTime now = LocalDateTime.now();
        WorkOrder wo = new WorkOrder();
        wo.setTicketNo(IdGenerator.workOrderNo());
        wo.setUserId(userId);
        wo.setType(type);
        wo.setPriority(priority);
        wo.setStatus(WorkOrderStateMachine.OPEN);
        wo.setChannel(channel);
        wo.setSubject(subject.trim());
        wo.setDescription(description.trim());
        wo.setOrderNo(orderNo == null || orderNo.isBlank() ? null : orderNo.trim());
        wo.setDisputeNo(disputeNo == null || disputeNo.isBlank() ? null : disputeNo.trim());
        // SLA：以建单时刻为起算点，按优先级落首响/解决到期时间（后续扫描与流转据此判定超时）
        wo.setFirstResponseDeadline(WorkOrderSlaPolicy.firstResponseDeadline(now, priority));
        wo.setResolveDeadline(WorkOrderSlaPolicy.resolveDeadline(now, priority));
        wo.setSlaBreached(0);
        workOrderMapper.insert(wo);
        metrics.increment("workorder.created");
        // 工单创建即可见：发布 WORKORDER_OPENED（聚合根 = 工单号），供风控/数据/通知下游消费
        eventPublisher.publish(EventType.WORKORDER_OPENED, wo.getTicketNo(),
                DomainEventPublisher.payload(
                        "workOrderId", wo.getId(),
                        "ticketNo", wo.getTicketNo(),
                        "userId", userId,
                        "type", type,
                        "priority", priority,
                        "disputeNo", wo.getDisputeNo()));
        return wo.getId();
    }

    /**
     * 维权单转人工：校验维权单存在、归属当前用户、仍处于未结状态，并做同维权单未结工单幂等，
     * 随后按 {@link WorkOrderDisputeLink} 映射的类型/优先级/标题派生客服工单。
     *
     * @param userId      发起用户（维权单的买家或卖家）
     * @param disputeNo   维权工单号
     * @param channel     来源渠道
     * @param description 补充描述（可空，缺省由维权诉求生成）
     * @return 工单 ID
     */
    public Long createFromDispute(Long userId, String disputeNo, String channel, String description) {
        if (disputeNo == null || disputeNo.isBlank()) {
            throw new BizException(Code.PARAM_MISSING, "维权工单号不能为空");
        }
        if (!VALID_CHANNELS.contains(channel)) {
            throw new BizException(Code.WORKORDER_CHANNEL_INVALID, "工单来源渠道不合法");
        }
        Dispute d = disputeMapper.selectOne(new LambdaQueryWrapper<Dispute>()
                .eq(Dispute::getDisputeNo, disputeNo.trim()));
        if (d == null) {
            throw new BizException(Code.DISPUTE_NOT_FOUND, "维权工单不存在");
        }
        // 归属：仅维权双方（买家/卖家）可就本维权单转人工，杜绝越权拉入无关工单
        if (!Objects.equals(userId, d.getBuyerId()) && !Objects.equals(userId, d.getSellerId())) {
            throw new BizException(Code.FORBIDDEN, "仅该维权单的买卖双方可转人工");
        }
        if (!WorkOrderDisputeLink.canConvert(d.getStatus())) {
            throw new BizException(Code.DISPUTE_STATE_NOT_ALLOWED,
                    DisputeStateMachine.isTerminal(d.getStatus()) ? "该维权工单已结案，无需转人工" : "当前维权状态不可转人工");
        }
        // 幂等：同一维权单仅允许存在一个未结客服工单
        long openWorkOrders = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getDisputeNo, d.getDisputeNo())
                .in(WorkOrder::getStatus, WorkOrderStateMachine.OPEN,
                        WorkOrderStateMachine.IN_PROGRESS, WorkOrderStateMachine.WAITING_USER));
        if (openWorkOrders > 0) {
            throw new BizException(Code.WORKORDER_DISPUTE_LINKED, "该维权工单已存在未结客服工单");
        }
        String type = WorkOrderDisputeLink.typeOf(d.getType());
        String priority = WorkOrderDisputeLink.priorityOf(d.getAmount(), d.getStatus());
        String subject = WorkOrderDisputeLink.subjectOf(d.getDisputeNo(), d.getType());
        String detail = (description == null || description.isBlank())
                ? WorkOrderDisputeLink.defaultDescription(d.getType(), d.getExpectation())
                : description.trim();
        return create(userId, type, priority, channel, subject, detail, d.getOrderNo(), d.getDisputeNo());
    }

    /**
     * 分配受理客服（OPEN/IN_PROGRESS/WAITING_USER → IN_PROGRESS）。
     * 记录首次响应时间（SLA 度量），并通知用户工单已分配。
     */
    public void assign(Long adminId, Long id, Long assigneeId) {
        WorkOrder wo = requireWorkOrder(id);
        if (!WorkOrderStateMachine.isOpen(wo.getStatus())) {
            throw new BizException(Code.WORKORDER_STATE_NOT_ALLOWED, "当前工单状态不可分配");
        }
        LocalDateTime now = LocalDateTime.now();
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setAssigneeId(assigneeId);
        upd.setStatus(WorkOrderStateMachine.IN_PROGRESS);
        LocalDateTime firstResponseAt = wo.getFirstResponseAt() == null ? now : wo.getFirstResponseAt();
        if (wo.getFirstResponseAt() == null) {
            upd.setFirstResponseAt(firstResponseAt);
        }
        // SLA：首响是否超出到期时间（分配即视为首次响应）
        if (WorkOrderSlaPolicy.firstResponseBreached(wo.getCreatedAt(), firstResponseAt, wo.getPriority(), now)) {
            upd.setSlaBreached(1);
            metrics.increment("workorder.sla.breached");
        }
        workOrderMapper.updateById(upd);
        metrics.increment("workorder.assigned");
        notifyUser(wo, NotificationType.WORKORDER_ASSIGNED, "工单已分配",
                "您的工单 " + wo.getTicketNo() + " 已分配给客服，请耐心等待处理");
    }

    /**
     * 管理员流转工单状态（ IN_PROGRESS/WAITING_USER/RESOLVED/CLOSED 等）。
     * 流转到 RESOLVED 时 {@code resolution} 必填；RESOLVED/CLOSED 发布对应事件并通知用户。
     */
    public void transition(Long adminId, Long id, String toStatus, String resolution) {
        WorkOrder wo = requireWorkOrder(id);
        if (!WorkOrderStateMachine.canTransition(wo.getStatus(), toStatus)) {
            throw new BizException(Code.WORKORDER_STATE_NOT_ALLOWED, "当前状态不允许流转到 " + toStatus);
        }
        boolean toResolved = WorkOrderStateMachine.RESOLVED.equals(toStatus);
        if (toResolved && (resolution == null || resolution.isBlank())) {
            throw new BizException(Code.PARAM_MISSING, "流转到已解决必须填写解决方案");
        }
        LocalDateTime now = LocalDateTime.now();
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setStatus(toStatus);
        if (toResolved) {
            upd.setResolution(resolution.trim());
            upd.setResolvedAt(now);
            // SLA：解决时间是否超出到期时间
            if (WorkOrderSlaPolicy.resolveBreached(wo.getCreatedAt(), now, wo.getPriority(), now)) {
                upd.setSlaBreached(1);
                metrics.increment("workorder.sla.breached");
            }
        }
        workOrderMapper.updateById(upd);
        metrics.increment("workorder.transition");
        if (toResolved) {
            metrics.increment("workorder.resolved");
            eventPublisher.publish(EventType.WORKORDER_RESOLVED, wo.getTicketNo(),
                    DomainEventPublisher.payload(
                            "workOrderId", wo.getId(),
                            "ticketNo", wo.getTicketNo(),
                            "userId", wo.getUserId(),
                            "assigneeId", wo.getAssigneeId()));
            notifyUser(wo, NotificationType.WORKORDER_RESOLVED, "工单已解决",
                    "您的工单 " + wo.getTicketNo() + " 已处理完成：" + resolution);
        } else if (WorkOrderStateMachine.WAITING_USER.equals(toStatus)) {
            notifyUser(wo, NotificationType.WORKORDER_WAITING, "工单待补充",
                    "客服在工单 " + wo.getTicketNo() + " 中需要您补充信息");
        }
    }

    /**
     * 用户关闭工单并满意度回访（任意未结状态 → CLOSED）。仅发起用户可关闭，且必须回填 1-5 满意度。
     */
    public void closeByUser(Long userId, Long id, Integer satisfaction) {
        if (satisfaction == null || satisfaction < 1 || satisfaction > 5) {
            throw new BizException(Code.WORKORDER_SATISFACTION_INVALID, "满意度评分不合法（应为 1-5）");
        }
        WorkOrder wo = requireWorkOrder(id);
        if (!Objects.equals(userId, wo.getUserId())) {
            throw new BizException(Code.FORBIDDEN, "仅工单发起用户可关闭");
        }
        if (WorkOrderStateMachine.isTerminal(wo.getStatus())) {
            throw new BizException(Code.WORKORDER_STATE_NOT_ALLOWED, "该工单已关闭");
        }
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setStatus(WorkOrderStateMachine.CLOSED);
        upd.setSatisfaction(satisfaction);
        upd.setClosedAt(LocalDateTime.now());
        workOrderMapper.updateById(upd);
        metrics.increment("workorder.closed");
        eventPublisher.publish(EventType.WORKORDER_CLOSED, wo.getTicketNo(),
                DomainEventPublisher.payload(
                        "workOrderId", wo.getId(),
                        "ticketNo", wo.getTicketNo(),
                        "userId", wo.getUserId(),
                        "satisfaction", satisfaction));
        // 通知受理客服（若有），best-effort
        if (wo.getAssigneeId() != null) {
            try {
                notificationService.notify(wo.getAssigneeId(), NotificationType.WORKORDER_CLOSED, wo.getTicketNo(),
                        "工单已关闭", "用户已关闭工单 " + wo.getTicketNo() + "，满意度 " + satisfaction + " 分");
            } catch (Exception ignored) {
            }
        }
    }

    /** 我的工单（分页，按创建时间倒序）。 */
    public IPage<WorkOrder> myList(Long userId, int page, int size) {
        LambdaQueryWrapper<WorkOrder> w = new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getUserId, userId)
                .orderByDesc(WorkOrder::getCreatedAt);
        return workOrderMapper.selectPage(new Page<>(page, size), w);
    }

    /** 工单详情（校验发起人可见）。 */
    public WorkOrder userDetail(Long userId, Long id) {
        WorkOrder wo = requireWorkOrder(id);
        if (!Objects.equals(userId, wo.getUserId())) {
            throw new BizException(Code.FORBIDDEN, "无权查看该工单");
        }
        return wo;
    }

    /** 后台工单详情（运营视角，不做归属校验）。 */
    public WorkOrder adminDetail(Long id) {
        return requireWorkOrder(id);
    }

    /** 工单关联的维权纠纷简要（校验发起人可见）；工单未关联维权单时返回空 VO。 */
    public WorkOrderLinkedDisputeVO userLinkedDispute(Long userId, Long id) {
        WorkOrder wo = requireWorkOrder(id);
        if (!Objects.equals(userId, wo.getUserId())) {
            throw new BizException(Code.FORBIDDEN, "无权查看该工单");
        }
        return toLinkedDisputeVO(wo.getDisputeNo());
    }

    /** 后台视角：工单关联的维权纠纷简要（运营视角，不做归属校验）。 */
    public WorkOrderLinkedDisputeVO adminLinkedDispute(Long id) {
        return toLinkedDisputeVO(requireWorkOrder(id).getDisputeNo());
    }

    /** 后台工单分页（status/type/priority 可选；keyword 匹配工单号/订单号/标题）。 */
    public IPage<WorkOrder> adminPage(String status, String type, String priority, String keyword, int page, int size) {
        LambdaQueryWrapper<WorkOrder> w = new LambdaQueryWrapper<WorkOrder>();
        if (status != null && !status.isBlank()) {
            w.eq(WorkOrder::getStatus, status);
        }
        if (type != null && !type.isBlank()) {
            w.eq(WorkOrder::getType, type);
        }
        if (priority != null && !priority.isBlank()) {
            w.eq(WorkOrder::getPriority, priority);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(WorkOrder::getTicketNo, keyword)
                    .or().like(WorkOrder::getOrderNo, keyword)
                    .or().like(WorkOrder::getSubject, keyword));
        }
        w.orderByDesc(WorkOrder::getCreatedAt);
        return workOrderMapper.selectPage(new Page<>(page, size), w);
    }

    private void notifyUser(WorkOrder wo, NotificationType type, String title, String content) {
        try {
            notificationService.notify(wo.getUserId(), type, wo.getTicketNo(), title, content);
        } catch (Exception ignored) {
        }
    }

    private WorkOrderLinkedDisputeVO toLinkedDisputeVO(String disputeNo) {
        if (disputeNo == null || disputeNo.isBlank()) {
            return WorkOrderLinkedDisputeVO.empty();
        }
        Dispute d = disputeMapper.selectOne(new LambdaQueryWrapper<Dispute>()
                .eq(Dispute::getDisputeNo, disputeNo));
        if (d == null) {
            return WorkOrderLinkedDisputeVO.empty();
        }
        return new WorkOrderLinkedDisputeVO(d.getDisputeNo(), d.getOrderNo(), d.getStatus(), d.getType(),
                d.getExpectation(), d.getAmount(), d.getResult(), d.getRefundAmount(), d.getCreatedAt());
    }

    private WorkOrder requireWorkOrder(Long id) {
        WorkOrder wo = workOrderMapper.selectById(id);
        if (wo == null) {
            throw new BizException(Code.WORKORDER_NOT_FOUND, "客服工单不存在");
        }
        return wo;
    }
}
