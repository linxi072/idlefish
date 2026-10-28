package com.idlefish.trade.workorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.event.DomainEventPublisher;
import com.idlefish.trade.event.EventType;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.workorder.WorkOrderStateMachine;
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
 * <p>与维权纠纷（dispute）解耦：本服务只承载「通用客服流转」，不处理资金裁决；通知走 F-02 通知中心，
 * 均为 best-effort（内部 try/catch 不阻断主流程）。每次状态变更均发布领域事件供下游（风控/数据/通知）消费。
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
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;
    private final DomainEventPublisher eventPublisher;

    public WorkOrderService(WorkOrderMapper workOrderMapper, NotificationService notificationService,
                            MetricsRegistry metrics, DomainEventPublisher eventPublisher) {
        this.workOrderMapper = workOrderMapper;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 用户提交客服工单。校验类型/优先级/渠道合法性，落库 OPEN 状态并发布 WORKORDER_OPENED 事件。
     *
     * @return 工单 ID
     */
    public Long create(Long userId, String type, String priority, String channel,
                       String subject, String description, String orderNo) {
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
        workOrderMapper.insert(wo);
        metrics.increment("workorder.created");
        // 工单创建即可见：发布 WORKORDER_OPENED（聚合根 = 工单号），供风控/数据/通知下游消费
        eventPublisher.publish(EventType.WORKORDER_OPENED, wo.getTicketNo(),
                DomainEventPublisher.payload(
                        "workOrderId", wo.getId(),
                        "ticketNo", wo.getTicketNo(),
                        "userId", userId,
                        "type", type,
                        "priority", priority));
        return wo.getId();
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
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setAssigneeId(assigneeId);
        upd.setStatus(WorkOrderStateMachine.IN_PROGRESS);
        if (wo.getFirstResponseAt() == null) {
            upd.setFirstResponseAt(LocalDateTime.now());
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
        WorkOrder upd = new WorkOrder();
        upd.setId(id);
        upd.setStatus(toStatus);
        if (toResolved) {
            upd.setResolution(resolution.trim());
            upd.setResolvedAt(LocalDateTime.now());
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

    private WorkOrder requireWorkOrder(Long id) {
        WorkOrder wo = workOrderMapper.selectById(id);
        if (wo == null) {
            throw new BizException(Code.WORKORDER_NOT_FOUND, "客服工单不存在");
        }
        return wo;
    }
}
