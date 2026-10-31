package com.idlefish.trade.trade.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.enums.RefundStatus;
import com.idlefish.trade.common.idempotent.IdempotentService;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.member.service.GrowthValueService;
import com.idlefish.trade.trade.dto.RefundApplyDTO;
import com.idlefish.trade.trade.entity.FundFlowBuilder;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.Refund;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.trade.mapper.RefundMapper;
import com.idlefish.trade.trade.vo.RefundVO;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.notify.service.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款服务（PRD §4.4）：仅退款 / 退货退款、48h 自动同意、5 天平台介入、退款流水与库存恢复。
 * <p>
 * 数据访问下沉至 {@link RefundStore}，实体→VO 映射下沉至 {@link RefundViews}；本类仅保留
 * 业务编排、资金安全（CAS 出款）、领域事件发布与 best-effort 副作用（通知 / 成长值冲正 / 库存释放）。
 */
@Service
public class RefundService {

    private final RefundStore store;
    /** 历史遗留：声明但从未使用，仅为维持 11 参构造签名兼容既有装配与测试。 */
    private final PayOrderMapper payOrderMapper;
    private final PayService payService;
    private final ItemService itemService;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;
    /** ITER-F07：退款冲正成长值（交易成长值随退款扣回，负向流水，幂等）。 */
    private final GrowthValueService growthValueService;

    /** 幂等（F-18）：拦截重复退款申请，重复请求回放首次退款单号。 */
    private final IdempotentService idempotentService;

    /** 领域事件发布（ITER-T06）：退款完成事件 best-effort 发布，Outbox 同事务保证不丢。 */
    private final com.idlefish.trade.event.DomainEventPublisher eventPublisher;

    /** 退款申请幂等业务类型。 */
    private static final String BIZ_REFUND_APPLY = "refund.apply";

    /** 幂等 PROCESSING 过期秒数。 */
    private static final long IDEMPOTENT_TTL_SECONDS = 30L;

    public RefundService(RefundMapper refundMapper, OrderMapper orderMapper,
                         PayOrderMapper payOrderMapper, PayService payService,
                         ItemService itemService, FundFlowMapper fundFlowMapper,
                         NotificationService notificationService, MetricsRegistry metrics,
                         GrowthValueService growthValueService,
                         IdempotentService idempotentService,
                         com.idlefish.trade.event.DomainEventPublisher eventPublisher) {
        this.store = new RefundStore(refundMapper, orderMapper, fundFlowMapper);
        this.payOrderMapper = payOrderMapper;
        this.payService = payService;
        this.itemService = itemService;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.growthValueService = growthValueService;
        this.idempotentService = idempotentService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 申请退款。
     * <p>
     * 幂等（F-18）：同买家同订单同类型重复提交只执行一次，重复请求回放首次退款单号。
     * findActiveRefund 保留为业务级兜底（防重复生成进行中退款单），两层防护互不冲突。
     */
    public String apply(Long buyerId, RefundApplyDTO dto) {
        String bizKey = buyerId + ":" + dto.getOrderNo() + ":" + dto.getType();
        return idempotentService.execute(BIZ_REFUND_APPLY, bizKey, IDEMPOTENT_TTL_SECONDS,
                String.class, () -> doApply(buyerId, dto));
    }

    /** 实际退款申请逻辑。 */
    private String doApply(Long buyerId, RefundApplyDTO dto) {
        Order o = store.selectOrderByOrderNo(dto.getOrderNo());
        if (o == null) {
            throw new BizException(Code.ORDER_NOT_FOUND);
        }
        if (!o.getBuyerId().equals(buyerId)) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "无权操作该订单");
        }
        if (!OrderStatus.PAID.getCode().equals(o.getStatus())
                && !OrderStatus.SHIPPING.getCode().equals(o.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅已支付/运输中订单可申请退款");
        }
        Refund exist = store.selectActiveByOrderNo(dto.getOrderNo());
        if (exist != null) {
            return exist.getRefundNo();
        }
        Refund r = buildRefund(buyerId, dto, o);
        store.insertRefund(r);
        // F-02 通知中心：退款申请触达卖家（best-effort）
        notificationService.notify(r.getSellerId(), NotificationType.REFUND_APPLY, r.getRefundNo(), "退款申请",
                "买家对订单 " + dto.getOrderNo() + " 发起退款申请");
        metrics.increment("refund.apply");
        return r.getRefundNo();
    }

    /** 组装退款单实体（含 48h 自动同意 / 5 天平台介入时间，未落库）。 */
    private Refund buildRefund(Long buyerId, RefundApplyDTO dto, Order o) {
        Refund r = new Refund();
        r.setRefundNo(IdGenerator.refundNo());
        r.setOrderNo(dto.getOrderNo());
        r.setPayNo(o.getPayNo());
        r.setBuyerId(buyerId);
        r.setSellerId(o.getSellerId());
        r.setItemId(o.getItemId());
        r.setType(dto.getType());
        r.setAmount(dto.getAmount() != null ? dto.getAmount() : o.getPayAmount());
        r.setReason(dto.getReason());
        r.setStatus(RefundStatus.WAIT_SELLER.getCode());
        r.setAutoAgreeAt(LocalDateTime.now().plusHours(48));
        r.setPlatformAt(LocalDateTime.now().plusDays(5));
        return r;
    }

    /** 卖家同意退款 → 发起退款。 */
    public void agree(Long sellerId, String refundNo) {
        Refund r = ownedSeller(sellerId, refundNo);
        if (!RefundStatus.WAIT_SELLER.getCode().equals(r.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "当前状态不可同意");
        }
        doRefund(r);
    }

    /** 卖家拒绝。 */
    public void reject(Long sellerId, String refundNo, String reason) {
        Refund r = ownedSeller(sellerId, refundNo);
        if (!RefundStatus.WAIT_SELLER.getCode().equals(r.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "当前状态不可拒绝");
        }
        Refund upd = new Refund();
        upd.setId(r.getId());
        upd.setStatus(RefundStatus.REJECTED.getCode());
        upd.setReason(reason);
        store.updateById(upd);
        // F-02 通知中心：拒绝退款触达买家（best-effort）
        notificationService.notify(r.getBuyerId(), NotificationType.REFUND_REJECTED, refundNo, "退款被拒绝",
                "卖家拒绝了您的退款申请" + (reason != null ? ("：" + reason) : ""));
    }

    /** 买家填写退货物流（退货退款）。 */
    public void returnLogistics(Long buyerId, String refundNo, String logisticsNo) {
        Refund r = ownedBuyer(buyerId, refundNo);
        if (!RefundStatus.WAIT_SELLER.getCode().equals(r.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "当前状态不可填写退货物流");
        }
        Refund upd = new Refund();
        upd.setId(r.getId());
        upd.setLogisticsNo(logisticsNo);
        store.updateById(upd);
    }

    /** 卖家确认收货并退款（退货退款）。 */
    public void confirmReturn(Long sellerId, String refundNo) {
        Refund r = ownedSeller(sellerId, refundNo);
        if (!RefundStatus.WAIT_SELLER.getCode().equals(r.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "当前状态不可确认");
        }
        doRefund(r);
    }

    /** 平台介入。 */
    public void platformIntervene(String refundNo) {
        Refund r = get(refundNo);
        if (RefundStatus.REFUNDED.getCode().equals(r.getStatus())
                || RefundStatus.CANCELED.getCode().equals(r.getStatus())
                || RefundStatus.REJECTED.getCode().equals(r.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "当前状态不可介入");
        }
        Refund upd = new Refund();
        upd.setId(r.getId());
        upd.setStatus(RefundStatus.PLATFORM.getCode());
        store.updateById(upd);
        // F-02 通知中心：平台介入触达买卖双方（best-effort）
        notificationService.notify(r.getBuyerId(), NotificationType.REFUND_PLATFORM, refundNo, "平台介入",
                "您的退款单 " + refundNo + " 已由平台介入处理");
        notificationService.notify(r.getSellerId(), NotificationType.REFUND_PLATFORM, refundNo, "平台介入",
                "退款单 " + refundNo + " 已由平台介入处理");
    }

    /** 后台强制同意最新一笔退款单。 */
    public void adminAgree(String orderNo) {
        Refund r = store.selectLatestByOrderNo(orderNo);
        if (r == null) {
            throw new BizException(Code.ORDER_NOT_FOUND, "未找到该订单的退款单");
        }
        if (!RefundStatus.WAIT_SELLER.getCode().equals(r.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "当前退款单状态不可同意");
        }
        doRefund(r);
    }

    /** 买家撤销退款。 */
    public void cancel(Long buyerId, String refundNo) {
        Refund r = ownedBuyer(buyerId, refundNo);
        if (RefundStatus.REFUNDED.getCode().equals(r.getStatus())
                || RefundStatus.REJECTED.getCode().equals(r.getStatus())
                || RefundStatus.CANCELED.getCode().equals(r.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "当前状态不可撤销");
        }
        Refund upd = new Refund();
        upd.setId(r.getId());
        upd.setStatus(RefundStatus.CANCELED.getCode());
        store.updateById(upd);
        // F-02 通知中心：撤销退款触达卖家（best-effort）
        notificationService.notify(r.getSellerId(), NotificationType.REFUND_CANCELED, refundNo, "退款撤销",
                "买家撤销了对订单 " + r.getOrderNo() + " 的退款申请");
    }

    public RefundVO detail(String refundNo) {
        return RefundViews.toVO(get(refundNo));
    }

    public List<RefundVO> listByOrder(String orderNo) {
        return RefundViews.toVOList(store.selectByOrderNo(orderNo));
    }

    /** 定时：48h 卖家未处理自动同意退款。 */
    public void autoAgree() {
        List<Refund> list = store.selectAutoAgreeDue(LocalDateTime.now());
        for (Refund r : list) {
            doRefund(r);
        }
    }

    /** 定时：5 天未解决平台介入。 */
    public void autoPlatform() {
        List<Refund> list = store.selectAutoPlatformDue(LocalDateTime.now());
        for (Refund r : list) {
            Refund upd = new Refund();
            upd.setId(r.getId());
            upd.setStatus(RefundStatus.PLATFORM.getCode());
            store.updateById(upd);
            // F-02 通知中心：超时平台介入触达买卖双方（best-effort）
            notificationService.notify(r.getBuyerId(), NotificationType.REFUND_PLATFORM, r.getRefundNo(), "平台介入",
                    "退款单 " + r.getRefundNo() + " 超时未处理，已由平台介入");
            notificationService.notify(r.getSellerId(), NotificationType.REFUND_PLATFORM, r.getRefundNo(), "平台介入",
                    "退款单 " + r.getRefundNo() + " 超时未处理，已由平台介入");
        }
    }

    // ---------- 内部工具 ----------

    private void doRefund(Refund r) {
        // T01 生产化：以 timedRun 包裹退款出款全过程，输出结构化 span（REQ-04）并采集耗时，
        // 供 P99 SLO 告警与链路追踪。
        metrics.timedRun("refund.pay", () -> doRefundInner(r));
    }

    private void doRefundInner(Refund r) {
        // P0-2 并发安全：以 CAS 将状态由 wait_seller 推进到 refunding，
        // 仅当 affected==1 才执行真实出款，杜绝并发/集群下重复退款
        // （如「手动同意」与「48h 自动同意」定时任务竞态、双节点同时触发）。
        int affected = store.casToRefunding(r.getId());
        if (affected == 0) {
            // 状态已被其它线程/节点推进（本节点为竞态失败方），放弃执行真实出款，避免双退。
            // REQ-07：记一次被 CAS 拦截的重复退款尝试，供资损级重复流水告警。
            metrics.increment("refund.cas.skip");
            metrics.increment("fund.flow.duplicate");
            return;
        }

        payService.refund(r.getPayNo(), r.getAmount());

        store.casToRefunded(r.getId(), LocalDateTime.now());
        metrics.increment("refund.success");

        // ITER-T06：REFUND_EXECUTED + ORDER_REFUNDED 事件（退款闭环，REQ-04）
        eventPublisher.publish(com.idlefish.trade.event.EventType.REFUND_EXECUTED, r.getRefundNo(),
                com.idlefish.trade.event.DomainEventPublisher.payload("orderNo", r.getOrderNo(), "buyerId", r.getBuyerId(),
                        "amountFen", r.getAmount()));
        eventPublisher.publish(com.idlefish.trade.event.EventType.ORDER_REFUNDED, r.getOrderNo(),
                com.idlefish.trade.event.DomainEventPublisher.payload("refundNo", r.getRefundNo(), "buyerId", r.getBuyerId(),
                        "amountFen", r.getAmount()));

        // 退款为原路退回，不计入钱包余额口径
        recordRefundFlow(r);

        // F-02 通知中心：退款成功触达买家（best-effort）
        notifyRefundSuccess(r);

        // ITER-F07：退款冲正成长值（按订单号幂等扣回交易成长值，负向流水；best-effort 不阻断退款）
        try {
            growthValueService.reverseByOrder(r.getOrderNo());
        } catch (Exception ignore) {
        }

        closeOrderIfPresent(r.getOrderNo());
        if ("return_refund".equals(r.getType())) {
            itemService.releaseStock(r.getItemId(), 1);
        }
    }

    /** 记录退款资金流入流水（原路退回，不计入钱包余额口径）。 */
    private void recordRefundFlow(Refund r) {
        store.insertFundFlow(FundFlowBuilder.of(r.getRefundNo(), r.getBuyerId(), "IN", "REFUND", r.getAmount())
                .balanceAfter(0L).build());
    }

    /** F-02 通知中心：退款成功触达买家（best-effort）。 */
    private void notifyRefundSuccess(Refund r) {
        notificationService.notify(r.getBuyerId(), NotificationType.REFUND_SUCCESS, r.getRefundNo(), "退款成功",
                "订单 " + r.getOrderNo() + " 的退款已原路退回 " + (r.getAmount() == null ? "" : (r.getAmount() / 100.0)) + " 元");
    }

    /** 退款完成后关闭关联订单（若存在）。 */
    private void closeOrderIfPresent(String orderNo) {
        Order o = store.selectOrderByOrderNo(orderNo);
        if (o != null) {
            store.closeOrder(o.getId());
        }
    }

    private Refund get(String refundNo) {
        Refund r = store.selectOneByRefundNo(refundNo);
        if (r == null) {
            throw new BizException(Code.ORDER_NOT_FOUND);
        }
        return r;
    }

    private Refund ownedBuyer(Long buyerId, String refundNo) {
        Refund r = get(refundNo);
        if (!r.getBuyerId().equals(buyerId)) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "无权操作该退款单");
        }
        return r;
    }

    private Refund ownedSeller(Long sellerId, String refundNo) {
        Refund r = get(refundNo);
        if (!r.getSellerId().equals(sellerId)) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "无权操作该退款单");
        }
        return r;
    }
}
