package com.idlefish.trade.dispute.service;

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
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.OrderMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 售后维权工单服务（F-17）：发起 / 举证 / 平台介入 / 裁决 / 撤销 / 归档。
 *
 * <p>资金安全约定：裁决仅落库「裁决结果与裁决退款金额」，<b>不直接发起退款出款</b>——
 * 实际资金动作交由既有退款链路（F-07/F-08）执行，避免重复实现资金操作造成资损。
 */
@Service
public class DisputeService {

    // 争议类型
    public static final String TYPE_REFUND_REJECTED = "REFUND_REJECTED";
    public static final String TYPE_NOT_RECEIVED = "NOT_RECEIVED";
    public static final String TYPE_DAMAGED = "DAMAGED";
    public static final String TYPE_NOT_AS_DESC = "NOT_AS_DESC";
    // 诉求
    public static final String EXPECT_REFUND = "REFUND";
    public static final String EXPECT_RETURN_REFUND = "RETURN_REFUND";
    // 裁决结果
    public static final String RESULT_BUYER_WIN = "BUYER_WIN";
    public static final String RESULT_SELLER_WIN = "SELLER_WIN";
    public static final String RESULT_PARTIAL = "PARTIAL";

    private static final Set<String> VALID_TYPES = new HashSet<>(Arrays.asList(
            TYPE_REFUND_REJECTED, TYPE_NOT_RECEIVED, TYPE_DAMAGED, TYPE_NOT_AS_DESC));
    private static final Set<String> VALID_EXPECTS = new HashSet<>(Arrays.asList(
            EXPECT_REFUND, EXPECT_RETURN_REFUND));
    private static final Set<String> VALID_RESULTS = new HashSet<>(Arrays.asList(
            RESULT_BUYER_WIN, RESULT_SELLER_WIN, RESULT_PARTIAL));

    private final DisputeMapper disputeMapper;
    private final OrderMapper orderMapper;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;

    public DisputeService(DisputeMapper disputeMapper, OrderMapper orderMapper,
                          NotificationService notificationService, MetricsRegistry metrics) {
        this.disputeMapper = disputeMapper;
        this.orderMapper = orderMapper;
        this.notificationService = notificationService;
        this.metrics = metrics;
    }

    /**
     * 买家发起维权。校验订单归属、争议类型/诉求合法性，并保证同一订单不存在未结工单。
     *
     * @return 工单 ID
     */
    public Long create(Long userId, String orderNo, String type, String expectation,
                       String reason, Long amount, String buyerEvidence) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BizException(Code.PARAM_MISSING, "订单号不能为空");
        }
        if (!VALID_TYPES.contains(type)) {
            throw new BizException(Code.PARAM_INVALID, "争议类型不合法");
        }
        if (!VALID_EXPECTS.contains(expectation)) {
            throw new BizException(Code.PARAM_INVALID, "维权诉求不合法");
        }
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (o == null) {
            throw new BizException(Code.ORDER_NOT_FOUND, "订单不存在");
        }
        if (!userId.equals(o.getBuyerId())) {
            throw new BizException(Code.FORBIDDEN, "仅订单买家可发起维权");
        }
        // 同一订单仅允许存在一个未结工单（PENDING/SELLER_REPLIED/PLATFORM）
        long open = disputeMapper.selectCount(new LambdaQueryWrapper<Dispute>()
                .eq(Dispute::getOrderNo, orderNo)
                .in(Dispute::getStatus, DisputeStateMachine.PENDING,
                        DisputeStateMachine.SELLER_REPLIED, DisputeStateMachine.PLATFORM));
        if (open > 0) {
            throw new BizException(Code.DISPUTE_EXISTS, "该订单存在未结维权工单");
        }

        Dispute d = new Dispute();
        d.setDisputeNo(IdGenerator.disputeNo());
        d.setOrderNo(orderNo);
        d.setBuyerId(userId);
        d.setSellerId(o.getSellerId());
        d.setType(type);
        d.setExpectation(expectation);
        d.setReason(reason == null ? "" : reason.trim());
        d.setAmount(amount == null || amount <= 0 ? (o.getPayAmount() == null ? 0 : o.getPayAmount()) : amount);
        d.setBuyerEvidence(buyerEvidence);
        d.setStatus(DisputeStateMachine.PENDING);
        d.setRefundAmount(0L);
        disputeMapper.insert(d);
        metrics.increment("dispute.created");

        // 通知卖家（best-effort，不影响工单创建）
        try {
            notificationService.notify(o.getSellerId(), NotificationType.DISPUTE_CREATED, d.getDisputeNo(),
                    "买家发起维权", "订单 " + orderNo + " 买家发起维权，请及时举证处理");
        } catch (Exception ignored) {
        }
        return d.getId();
    }

    /** 卖家举证（PENDING → SELLER_REPLIED）。 */
    public void sellerReply(Long disputeId, Long sellerId, String evidence) {
        Dispute d = requireDispute(disputeId);
        if (!Objects.equals(sellerId, d.getSellerId())) {
            throw new BizException(Code.FORBIDDEN, "仅该订单卖家可举证");
        }
        if (!DisputeStateMachine.canTransition(d.getStatus(), DisputeStateMachine.SELLER_REPLIED)) {
            throw new BizException(Code.DISPUTE_STATE_NOT_ALLOWED, "当前状态不可举证");
        }
        Dispute upd = new Dispute();
        upd.setId(disputeId);
        upd.setSellerEvidence(evidence);
        upd.setStatus(DisputeStateMachine.SELLER_REPLIED);
        disputeMapper.updateById(upd);
        metrics.increment("dispute.seller_reply");
        try {
            notificationService.notify(d.getBuyerId(), NotificationType.DISPUTE_CREATED, d.getDisputeNo(),
                    "卖家已举证", "订单 " + d.getOrderNo() + " 卖家已提交举证材料，可申请平台介入");
        } catch (Exception ignored) {
        }
    }

    /** 申请平台介入（PENDING/SELLER_REPLIED → PLATFORM）。买家或卖家均可申请。 */
    public void applyPlatform(Long disputeId, Long userId) {
        Dispute d = requireDispute(disputeId);
        if (!Objects.equals(userId, d.getBuyerId()) && !Objects.equals(userId, d.getSellerId())) {
            throw new BizException(Code.FORBIDDEN, "无权操作该工单");
        }
        if (!DisputeStateMachine.canApplyPlatform(d.getStatus())) {
            throw new BizException(Code.DISPUTE_STATE_NOT_ALLOWED, "当前状态不可申请平台介入");
        }
        Dispute upd = new Dispute();
        upd.setId(disputeId);
        upd.setStatus(DisputeStateMachine.PLATFORM);
        disputeMapper.updateById(upd);
        metrics.increment("dispute.platform");
        // 双方均触达（best-effort）
        try {
            notificationService.notify(d.getBuyerId(), NotificationType.DISPUTE_PLATFORM, d.getDisputeNo(),
                    "平台已介入", "工单 " + d.getDisputeNo() + " 已申请平台介入，请等待裁决");
            notificationService.notify(d.getSellerId(), NotificationType.DISPUTE_PLATFORM, d.getDisputeNo(),
                    "平台已介入", "工单 " + d.getDisputeNo() + " 已申请平台介入，请配合处理");
        } catch (Exception ignored) {
        }
    }

    /** 买家撤销（PENDING/SELLER_REPLIED → CANCELED）。 */
    public void cancel(Long disputeId, Long userId) {
        Dispute d = requireDispute(disputeId);
        if (!Objects.equals(userId, d.getBuyerId())) {
            throw new BizException(Code.FORBIDDEN, "仅申请人可撤销");
        }
        if (!DisputeStateMachine.canBuyerCancel(d.getStatus())) {
            throw new BizException(Code.DISPUTE_STATE_NOT_ALLOWED, "当前状态不可撤销");
        }
        Dispute upd = new Dispute();
        upd.setId(disputeId);
        upd.setStatus(DisputeStateMachine.CANCELED);
        disputeMapper.updateById(upd);
        metrics.increment("dispute.canceled");
    }

    /**
     * 平台裁决（→ RESOLVED）。仅落库裁决结果与裁决退款金额，不直接发起出款。
     *
     * @param result       裁决结果 BUYER_WIN / SELLER_WIN / PARTIAL
     * @param refundAmount 裁决退款金额（分）；卖家胜则为 0
     */
    public void resolve(Long disputeId, String result, Long refundAmount, String remark) {
        Dispute d = requireDispute(disputeId);
        if (!VALID_RESULTS.contains(result)) {
            throw new BizException(Code.PARAM_INVALID, "裁决结果不合法");
        }
        if (!DisputeStateMachine.canResolve(d.getStatus())) {
            throw new BizException(Code.DISPUTE_STATE_NOT_ALLOWED, "当前状态不可裁决");
        }
        long refund = RESULT_SELLER_WIN.equals(result) ? 0L : (refundAmount == null ? 0L : refundAmount);
        // 退款金额不得超过争议金额（防超额资损）
        long cap = d.getAmount() == null ? 0L : d.getAmount();
        if (refund > cap) {
            refund = cap;
        }
        Dispute upd = new Dispute();
        upd.setId(disputeId);
        upd.setStatus(DisputeStateMachine.RESOLVED);
        upd.setResult(result);
        upd.setRefundAmount(refund);
        upd.setPlatformRemark(remark);
        upd.setHandledAt(LocalDateTime.now());
        disputeMapper.updateById(upd);
        metrics.increment("dispute.resolved");
        try {
            String text = "工单 " + d.getDisputeNo() + " 裁决结果：" + result
                    + (refund > 0 ? "，退款 " + refund / 100.0 + " 元" : "");
            notificationService.notify(d.getBuyerId(), NotificationType.DISPUTE_RESOLVED, d.getDisputeNo(),
                    "维权已裁决", text);
            notificationService.notify(d.getSellerId(), NotificationType.DISPUTE_RESOLVED, d.getDisputeNo(),
                    "维权已裁决", text);
        } catch (Exception ignored) {
        }
    }

    /** 归档关闭（RESOLVED/CANCELED → CLOSED）。 */
    public void close(Long disputeId) {
        Dispute d = requireDispute(disputeId);
        if (!DisputeStateMachine.canTransition(d.getStatus(), DisputeStateMachine.CLOSED)) {
            throw new BizException(Code.DISPUTE_STATE_NOT_ALLOWED, "当前状态不可关闭");
        }
        Dispute upd = new Dispute();
        upd.setId(disputeId);
        upd.setStatus(DisputeStateMachine.CLOSED);
        disputeMapper.updateById(upd);
    }

    /** 我的维权（买家或卖家视角，按创建时间倒序）。 */
    public IPage<Dispute> myList(Long userId, int page, int size) {
        LambdaQueryWrapper<Dispute> w = new LambdaQueryWrapper<Dispute>()
                .eq(Dispute::getBuyerId, userId).or().eq(Dispute::getSellerId, userId)
                .orderByDesc(Dispute::getCreatedAt);
        return disputeMapper.selectPage(new Page<>(page, size), w);
    }

    /** 工单详情（校验买卖双方可见）。 */
    public Dispute detail(Long disputeId, Long userId) {
        Dispute d = requireDispute(disputeId);
        if (!Objects.equals(userId, d.getBuyerId()) && !Objects.equals(userId, d.getSellerId())) {
            throw new BizException(Code.FORBIDDEN, "无权查看该工单");
        }
        return d;
    }

    /** 后台工单详情（运营视角，不做买卖双方校验）。 */
    public Dispute adminDetail(Long disputeId) {
        return requireDispute(disputeId);
    }

    /** 后台工单分页（status 可选；keyword 匹配工单号/订单号）。 */
    public IPage<Dispute> adminPage(String status, String keyword, int page, int size) {
        LambdaQueryWrapper<Dispute> w = new LambdaQueryWrapper<Dispute>();
        if (status != null && !status.isBlank()) {
            w.eq(Dispute::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(Dispute::getDisputeNo, keyword).or().like(Dispute::getOrderNo, keyword));
        }
        w.orderByDesc(Dispute::getCreatedAt);
        return disputeMapper.selectPage(new Page<>(page, size), w);
    }

    private Dispute requireDispute(Long disputeId) {
        Dispute d = disputeMapper.selectById(disputeId);
        if (d == null) {
            throw new BizException(Code.DISPUTE_NOT_FOUND, "维权工单不存在");
        }
        return d;
    }
}
