package com.idlefish.trade.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.inspection.InspectionFeeCalculator;
import com.idlefish.trade.inspection.InspectionStateMachine;
import com.idlefish.trade.inspection.dto.InspectionResultDTO;
import com.idlefish.trade.inspection.entity.InspectionOrder;
import com.idlefish.trade.inspection.entity.InspectionReport;
import com.idlefish.trade.inspection.mapper.InspectionOrderMapper;
import com.idlefish.trade.inspection.mapper.InspectionReportMapper;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.dto.RefundApplyDTO;
import com.idlefish.trade.trade.entity.FundFlowBuilder;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.service.RefundService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 鉴定验货履约服务（F-02）：送检 / 收件 / 验货 / 回传 / 取消 / 异常处置 / 发货冻结。
 *
 * <p>资金安全约定：
 * <ul>
 *   <li>REQ-05 验货不通过自动退款复用既有退款链路（F-07/F-08），本服务<b>不直接发起出款</b>，避免重复资金实现；</li>
 *   <li>REQ-10 服务费按「分」记入资金流水（type=INSPECTION），参与对账；</li>
 *   <li>REQ-09 超时/机构不可用归并为 EXCEPTION，且<b>绝不会</b>误判通过（PASSED 仅由机构显式回传触发）。</li>
 * </ul>
 */
@Service
public class InspectionService {

    public static final String TYPE_STANDARD = "STANDARD";
    public static final String TYPE_ACCURATE = "ACCURATE";

    private static final Set<String> VALID_TYPES = Set.of(TYPE_STANDARD, TYPE_ACCURATE);

    /** 验货超时阈值（小时）：机构收件后 72h 未完成视为异常（REQ-09）。 */
    private static final long INSPECTION_TIMEOUT_HOURS = 72L;

    private final InspectionOrderMapper inspectionOrderMapper;
    private final InspectionReportMapper inspectionReportMapper;
    private final OrderMapper orderMapper;
    private final ItemService itemService;
    private final RefundService refundService;
    private final FundFlowMapper fundFlowMapper;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;

    public InspectionService(InspectionOrderMapper inspectionOrderMapper,
                             InspectionReportMapper inspectionReportMapper,
                             OrderMapper orderMapper,
                             ItemService itemService,
                             RefundService refundService,
                             FundFlowMapper fundFlowMapper,
                             NotificationService notificationService,
                             MetricsRegistry metrics) {
        this.inspectionOrderMapper = inspectionOrderMapper;
        this.inspectionReportMapper = inspectionReportMapper;
        this.orderMapper = orderMapper;
        this.itemService = itemService;
        this.refundService = refundService;
        this.fundFlowMapper = fundFlowMapper;
        this.notificationService = notificationService;
        this.metrics = metrics;
    }

    /**
     * REQ-02 送检创建验货单（待收件）。不阻塞下单（REQ-01），但发货受冻结窗口约束（REQ-04）。
     *
     * @return 验货单 ID
     */
    public Long create(Long buyerId, String orderNo, String type, Long agencyId) {
        if (!VALID_TYPES.contains(type)) {
            throw new BizException(Code.PARAM_INVALID, "验货类型不合法");
        }
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (o == null) {
            throw new BizException(Code.ORDER_NOT_FOUND, "订单不存在");
        }
        if (!buyerId.equals(o.getBuyerId())) {
            throw new BizException(Code.FORBIDDEN, "仅订单买家可送检");
        }
        if (!OrderStatus.PAID.getCode().equals(o.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅已支付订单可送检");
        }
        // 同一订单仅允许一个进行中验货单（避免重复送检 / 重复收费）
        long open = inspectionOrderMapper.selectCount(new LambdaQueryWrapper<InspectionOrder>()
                .eq(InspectionOrder::getOrderNo, orderNo)
                .in(InspectionOrder::getStatus, InspectionStateMachine.WAIT_PICKUP,
                        InspectionStateMachine.IN_TRANSIT, InspectionStateMachine.INSPECTING));
        if (open > 0) {
            throw new BizException(Code.INSPECTION_EXISTS, "该订单已存在进行中的验货单");
        }

        long fee = InspectionFeeCalculator.calcServiceFee(o.getPayAmount() == null ? 0 : o.getPayAmount());
        InspectionOrder io = new InspectionOrder();
        io.setInspectionNo(IdGenerator.inspectionNo());
        io.setOrderNo(orderNo);
        io.setItemId(o.getItemId());
        io.setSellerId(o.getSellerId());
        io.setBuyerId(buyerId);
        io.setType(type);
        io.setStatus(InspectionStateMachine.WAIT_PICKUP);
        io.setFeeAmount(fee);
        io.setAgencyId(agencyId);
        io.setTimeoutAt(LocalDateTime.now().plusHours(INSPECTION_TIMEOUT_HOURS));
        inspectionOrderMapper.insert(io);

        // REQ-10 服务费入资金流水（买家支出，分），参与对账
        if (fee > 0) {
            fundFlowMapper.insert(FundFlowBuilder.of(io.getInspectionNo(), buyerId, "OUT", "INSPECTION", fee)
                    .balanceAfter(0L).build());
        }
        metrics.increment("inspection.created");
        notifySafely(buyerId, NotificationType.INSPECTION_CREATED, io.getInspectionNo(), "验货送检",
                "订单 " + orderNo + " 已送检，验货单 " + io.getInspectionNo());
        return io.getId();
    }

    /** REQ-03 机构收件（WAIT_PICKUP → IN_TRANSIT）。 */
    public void receiveItem(Long inspectionId, Long agencyId) {
        InspectionOrder io = require(inspectionId);
        if (!InspectionStateMachine.canReceive(io.getStatus())) {
            throw new BizException(Code.INSPECTION_STATE_NOT_ALLOWED, "当前状态不可收件");
        }
        InspectionOrder upd = new InspectionOrder();
        upd.setId(inspectionId);
        upd.setStatus(InspectionStateMachine.IN_TRANSIT);
        upd.setReceivedAt(LocalDateTime.now());
        if (agencyId != null) {
            upd.setAgencyId(agencyId);
        }
        inspectionOrderMapper.updateById(upd);
        metrics.increment("inspection.received");
        notifySafely(io.getBuyerId(), NotificationType.INSPECTION_RECEIVED, io.getInspectionNo(), "机构已收件",
                "验货单 " + io.getInspectionNo() + " 机构已收件，进入运输");
    }

    /** REQ-03 机构开始验货（IN_TRANSIT → INSPECTING）。 */
    public void startInspect(Long inspectionId) {
        InspectionOrder io = require(inspectionId);
        if (!InspectionStateMachine.canStartInspect(io.getStatus())) {
            throw new BizException(Code.INSPECTION_STATE_NOT_ALLOWED, "当前状态不可开始验货");
        }
        InspectionOrder upd = new InspectionOrder();
        upd.setId(inspectionId);
        upd.setStatus(InspectionStateMachine.INSPECTING);
        upd.setStartedAt(LocalDateTime.now());
        inspectionOrderMapper.updateById(upd);
        metrics.increment("inspection.started");
    }

    /**
     * REQ-03 机构回传结果：落库不可篡改报告快照 + 推进 PASSED/REJECTED。
     * REQ-09 安全边界：pass 仅由显式回传决定，超时不会误判通过（见 timeoutTarget）。
     */
    public void receiveResult(Long inspectionId, InspectionResultDTO dto) {
        InspectionOrder io = require(inspectionId);
        if (!InspectionStateMachine.canReceiveResult(io.getStatus())) {
            throw new BizException(Code.INSPECTION_STATE_NOT_ALLOWED, "当前状态不可回传结果");
        }
        boolean pass = Boolean.TRUE.equals(dto.getPass());

        // 落库报告快照（不可篡改存证，含机构原始报文）
        InspectionReport r = new InspectionReport();
        r.setReportNo(IdGenerator.reportNo());
        r.setInspectionNo(io.getInspectionNo());
        r.setOrderNo(io.getOrderNo());
        r.setItemId(io.getItemId());
        r.setAgencyId(io.getAgencyId());
        r.setGrade(dto.getGrade());
        r.setPass(pass);
        r.setFuncItems(dto.getFuncItems());
        r.setFlaws(dto.getFlaws());
        r.setCoverImages(dto.getCoverImages());
        r.setRawJson(dto.getRawJson());
        r.setReportVersion(dto.getReportVersion() == null || dto.getReportVersion().isBlank() ? "1.0" : dto.getReportVersion());
        inspectionReportMapper.insert(r);

        String to = pass ? InspectionStateMachine.PASSED : InspectionStateMachine.REJECTED;
        InspectionOrder upd = new InspectionOrder();
        upd.setId(inspectionId);
        upd.setStatus(to);
        upd.setReportId(r.getId());
        upd.setFinishedAt(LocalDateTime.now());
        inspectionOrderMapper.updateById(upd);
        metrics.increment(pass ? "inspection.passed" : "inspection.rejected");

        if (pass) {
            // REQ-07 通过打「已验」标识
            try {
                itemService.markInspected(io.getItemId());
            } catch (Exception ignored) {
            }
            notifySafely(io.getBuyerId(), NotificationType.INSPECTION_RESULT, io.getInspectionNo(), "验货通过",
                    "验货单 " + io.getInspectionNo() + " 鉴定通过（评级 " + (dto.getGrade() == null ? "" : dto.getGrade()) + "），可放心交易");
            notifySafely(io.getSellerId(), NotificationType.INSPECTION_RESULT, io.getInspectionNo(), "验货通过",
                    "验货单 " + io.getInspectionNo() + " 鉴定通过");
        } else {
            // REQ-05 不通过：自动关单 + 退款 + 通知（复用既有退款链路）
            autoRefundOnReject(io);
            notifySafely(io.getBuyerId(), NotificationType.INSPECTION_RESULT, io.getInspectionNo(), "验货不通过",
                    "验货单 " + io.getInspectionNo() + " 鉴定未通过，订单将自动退款关闭");
            notifySafely(io.getSellerId(), NotificationType.INSPECTION_RESULT, io.getInspectionNo(), "验货不通过",
                    "验货单 " + io.getInspectionNo() + " 鉴定未通过，订单将自动退款关闭");
        }
    }

    /** REQ-05 不通过自动退款：复用既有退款链路（apply + 立即执行），本服务不直接出款。 */
    private void autoRefundOnReject(InspectionOrder io) {
        try {
            RefundApplyDTO refund = new RefundApplyDTO();
            refund.setOrderNo(io.getOrderNo());
            refund.setType("only_refund");
            refund.setAmount(null); // 走订单实付金额
            refund.setReason("验货不通过，自动退款");
            refundService.apply(io.getBuyerId(), refund);
            refundService.adminAgree(io.getOrderNo()); // 立即执行退款并关单
        } catch (Exception e) {
            // 退款失败留痕，不阻断状态推进；后续定时/人工介入
            metrics.increment("inspection.refund.failed");
        }
    }

    /** 买家取消（WAIT_PICKUP/IN_TRANSIT → CANCELED）。验货开始后不可取消。 */
    public void cancel(Long inspectionId, Long userId) {
        InspectionOrder io = require(inspectionId);
        if (!Objects.equals(userId, io.getBuyerId())) {
            throw new BizException(Code.FORBIDDEN, "仅送检买家可取消");
        }
        if (!InspectionStateMachine.canCancel(io.getStatus())) {
            throw new BizException(Code.INSPECTION_STATE_NOT_ALLOWED, "当前状态不可取消");
        }
        InspectionOrder upd = new InspectionOrder();
        upd.setId(inspectionId);
        upd.setStatus(InspectionStateMachine.CANCELED);
        inspectionOrderMapper.updateById(upd);
        metrics.increment("inspection.canceled");
    }

    /**
     * REQ-09 运营处理验货异常（EXCEPTION → CANCELED）：仅可释放（解冻发货），绝不可误判通过。
     * 机构超时/不可用导致异常时，平台据此恢复订单履约。
     */
    public void adminResolveException(Long inspectionId, String remark) {
        InspectionOrder io = require(inspectionId);
        if (!InspectionStateMachine.EXCEPTION.equals(io.getStatus())) {
            throw new BizException(Code.INSPECTION_STATE_NOT_ALLOWED, "仅验货异常单可处理");
        }
        InspectionOrder upd = new InspectionOrder();
        upd.setId(inspectionId);
        upd.setStatus(InspectionStateMachine.CANCELED);
        upd.setRemark(remark);
        upd.setFinishedAt(LocalDateTime.now());
        inspectionOrderMapper.updateById(upd);
        metrics.increment("inspection.exception.resolved");
        notifySafely(io.getBuyerId(), NotificationType.INSPECTION_EXCEPTION, io.getInspectionNo(), "验货异常处理",
                "验货单 " + io.getInspectionNo() + " 机构异常已由平台处理，订单恢复履约");
    }

    /**
     * REQ-04 发货冻结窗口：订单存在进行中（待收件/运输中/验货中）验货单则冻结发货。
     * 由 OrderService.ship 调用，防止「验货中」商品被发出。
     */
    public boolean isShippingFrozen(String orderNo) {
        long open = inspectionOrderMapper.selectCount(new LambdaQueryWrapper<InspectionOrder>()
                .eq(InspectionOrder::getOrderNo, orderNo)
                .in(InspectionOrder::getStatus, InspectionStateMachine.WAIT_PICKUP,
                        InspectionStateMachine.IN_TRANSIT, InspectionStateMachine.INSPECTING));
        return open > 0;
    }

    /** REQ-06 工单可调阅报告：按订单号取最新报告（无则返回 null）。 */
    public InspectionReport getReportByOrder(String orderNo) {
        return inspectionReportMapper.selectOne(new LambdaQueryWrapper<InspectionReport>()
                .eq(InspectionReport::getOrderNo, orderNo)
                .orderByDesc(InspectionReport::getCreatedAt).last("LIMIT 1"));
    }

    public InspectionReport getReportById(Long reportId) {
        return inspectionReportMapper.selectById(reportId);
    }

    /** 我的验货单（买家视角，分页）。 */
    public IPage<InspectionOrder> myList(Long userId, int page, int size) {
        return inspectionOrderMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<InspectionOrder>().eq(InspectionOrder::getBuyerId, userId)
                        .orderByDesc(InspectionOrder::getCreatedAt));
    }

    /** 验货单详情（买卖双方可见）。 */
    public InspectionOrder detail(Long inspectionId, Long userId) {
        InspectionOrder io = require(inspectionId);
        if (!Objects.equals(userId, io.getBuyerId()) && !Objects.equals(userId, io.getSellerId())) {
            throw new BizException(Code.FORBIDDEN, "无权查看该验货单");
        }
        return io;
    }

    /** 运营验货单详情（不做买卖双方校验）。 */
    public InspectionOrder adminDetail(Long inspectionId) {
        return require(inspectionId);
    }

    /** 运营验货单分页（status 可选；keyword 匹配验货单号/订单号）。 */
    public IPage<InspectionOrder> adminPage(String status, String keyword, int page, int size) {
        LambdaQueryWrapper<InspectionOrder> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            w.eq(InspectionOrder::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(InspectionOrder::getInspectionNo, keyword)
                    .or().like(InspectionOrder::getOrderNo, keyword));
        }
        w.orderByDesc(InspectionOrder::getCreatedAt);
        return inspectionOrderMapper.selectPage(new Page<>(page, size), w);
    }

    private InspectionOrder require(Long inspectionId) {
        InspectionOrder io = inspectionOrderMapper.selectById(inspectionId);
        if (io == null) {
            throw new BizException(Code.INSPECTION_NOT_FOUND, "验货单不存在");
        }
        return io;
    }

    private void notifySafely(Long userId, NotificationType type, String bizNo, String title, String content) {
        try {
            notificationService.notify(userId, type, bizNo, title, content);
        } catch (Exception ignored) {
        }
    }
}
