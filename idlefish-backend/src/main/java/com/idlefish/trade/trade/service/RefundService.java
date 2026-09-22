package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.enums.RefundStatus;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.trade.dto.RefundApplyDTO;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.Refund;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.trade.mapper.RefundMapper;
import com.idlefish.trade.trade.vo.RefundVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 退款服务（PRD §4.4）：仅退款 / 退货退款、48h 自动同意、5 天平台介入、退款流水与库存恢复。
 */
@Service
public class RefundService {

    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final PayOrderMapper payOrderMapper;
    private final PayService payService;
    private final ItemService itemService;
    private final FundFlowMapper fundFlowMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public RefundService(RefundMapper refundMapper, OrderMapper orderMapper,
                         PayOrderMapper payOrderMapper, PayService payService,
                         ItemService itemService, FundFlowMapper fundFlowMapper) {
        this.refundMapper = refundMapper;
        this.orderMapper = orderMapper;
        this.payOrderMapper = payOrderMapper;
        this.payService = payService;
        this.itemService = itemService;
        this.fundFlowMapper = fundFlowMapper;
    }

    /** 申请退款（幂等：同订单进行中退款单则直接返回）。 */
    public String apply(Long buyerId, RefundApplyDTO dto) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, dto.getOrderNo()));
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
        Refund exist = refundMapper.selectOne(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderNo, dto.getOrderNo())
                .in(Refund::getStatus, List.of(
                        RefundStatus.WAIT_SELLER.getCode(),
                        RefundStatus.PLATFORM.getCode(),
                        RefundStatus.REFUNDING.getCode())));
        if (exist != null) {
            return exist.getRefundNo();
        }
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
        refundMapper.insert(r);
        return r.getRefundNo();
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
        refundMapper.updateById(upd);
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
        refundMapper.updateById(upd);
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
        refundMapper.updateById(upd);
    }

    /** 后台代处理退款同意（运营操作，跳过卖家归属校验，按订单号定位最新退款单）。 */
    public void adminAgree(String orderNo) {
        Refund r = refundMapper.selectOne(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderNo, orderNo)
                .orderByDesc(Refund::getCreatedAt).last("LIMIT 1"));
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
        refundMapper.updateById(upd);
    }

    public RefundVO detail(String refundNo) {
        Refund r = get(refundNo);
        RefundVO vo = new RefundVO();
        vo.setRefundNo(r.getRefundNo());
        vo.setOrderNo(r.getOrderNo());
        vo.setBuyerId(r.getBuyerId());
        vo.setSellerId(r.getSellerId());
        vo.setType(r.getType());
        vo.setAmount(r.getAmount());
        vo.setReason(r.getReason());
        vo.setStatus(r.getStatus());
        vo.setLogisticsNo(r.getLogisticsNo());
        vo.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().format(FMT));
        return vo;
    }

    public List<RefundVO> listByOrder(String orderNo) {
        return refundMapper.selectList(new LambdaQueryWrapper<Refund>().eq(Refund::getOrderNo, orderNo))
                .stream().map(r -> {
                    RefundVO vo = new RefundVO();
                    vo.setRefundNo(r.getRefundNo());
                    vo.setOrderNo(r.getOrderNo());
                    vo.setBuyerId(r.getBuyerId());
                    vo.setSellerId(r.getSellerId());
                    vo.setType(r.getType());
                    vo.setAmount(r.getAmount());
                    vo.setReason(r.getReason());
                    vo.setStatus(r.getStatus());
                    vo.setLogisticsNo(r.getLogisticsNo());
                    vo.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().format(FMT));
                    return vo;
                }).toList();
    }

    /** 定时：48h 卖家未处理自动同意退款。 */
    public void autoAgree() {
        List<Refund> list = refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getStatus, RefundStatus.WAIT_SELLER.getCode())
                .le(Refund::getAutoAgreeAt, LocalDateTime.now()));
        for (Refund r : list) {
            doRefund(r);
        }
    }

    /** 定时：5 天未解决平台介入。 */
    public void autoPlatform() {
        List<Refund> list = refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getStatus, RefundStatus.WAIT_SELLER.getCode())
                .le(Refund::getPlatformAt, LocalDateTime.now()));
        for (Refund r : list) {
            Refund upd = new Refund();
            upd.setId(r.getId());
            upd.setStatus(RefundStatus.PLATFORM.getCode());
            refundMapper.updateById(upd);
        }
    }

    // ---------- 内部工具 ----------

    private void doRefund(Refund r) {
        Refund mid = new Refund();
        mid.setId(r.getId());
        mid.setStatus(RefundStatus.REFUNDING.getCode());
        refundMapper.updateById(mid);

        payService.refund(r.getPayNo(), r.getAmount());

        Refund done = new Refund();
        done.setId(r.getId());
        done.setStatus(RefundStatus.REFUNDED.getCode());
        done.setRefundAt(LocalDateTime.now());
        refundMapper.updateById(done);

        FundFlow ff = new FundFlow();
        ff.setBizNo(r.getRefundNo());
        ff.setUserId(r.getBuyerId());
        ff.setDirection("IN");
        ff.setAmount(r.getAmount());
        ff.setType("REFUND");
        ff.setBalanceAfter(r.getAmount());
        fundFlowMapper.insert(ff);

        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, r.getOrderNo()));
        if (o != null) {
            Order ou = new Order();
            ou.setId(o.getId());
            ou.setStatus(OrderStatus.CLOSED.getCode());
            orderMapper.updateById(ou);
        }
        if ("return_refund".equals(r.getType())) {
            itemService.releaseStock(r.getItemId(), 1);
        }
    }

    private Refund get(String refundNo) {
        Refund r = refundMapper.selectOne(new LambdaQueryWrapper<Refund>().eq(Refund::getRefundNo, refundNo));
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
