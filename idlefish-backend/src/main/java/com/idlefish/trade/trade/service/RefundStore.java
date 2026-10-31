package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.enums.RefundStatus;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.Refund;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.RefundMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款数据访问（ITER-F03 范式）：退款单 / 订单 / 资金流水的读写与 CAS 状态推进，由 {@link RefundService} 构造器内持有。
 * 纯函数映射下沉至 {@link RefundViews}；本类只承载 Mapper 交互，不持有业务编排
 * （通知、指标、事件发布、成长值冲正等副作用留在服务层）。
 */
class RefundStore {

    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final FundFlowMapper fundFlowMapper;

    RefundStore(RefundMapper refundMapper, OrderMapper orderMapper, FundFlowMapper fundFlowMapper) {
        this.refundMapper = refundMapper;
        this.orderMapper = orderMapper;
        this.fundFlowMapper = fundFlowMapper;
    }

    // ===================== 读 =====================

    Refund selectOneByRefundNo(String refundNo) {
        return refundMapper.selectOne(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getRefundNo, refundNo));
    }

    /** 查询订单进行中的退款单（待卖家/平台/退款中任一），用于幂等去重。 */
    Refund selectActiveByOrderNo(String orderNo) {
        return refundMapper.selectOne(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderNo, orderNo)
                .in(Refund::getStatus, List.of(
                        RefundStatus.WAIT_SELLER.getCode(),
                        RefundStatus.PLATFORM.getCode(),
                        RefundStatus.REFUNDING.getCode())));
    }

    List<Refund> selectByOrderNo(String orderNo) {
        return refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderNo, orderNo));
    }

    /** 最新一笔退款单（按创建时间倒序，含 LIMIT 1），供后台强制同意使用。 */
    Refund selectLatestByOrderNo(String orderNo) {
        return refundMapper.selectOne(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderNo, orderNo)
                .orderByDesc(Refund::getCreatedAt).last("LIMIT 1"));
    }

    /** 定时：48h 卖家未处理自动同意（待卖家且 autoAgreeAt <= now）。 */
    List<Refund> selectAutoAgreeDue(LocalDateTime now) {
        return refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getStatus, RefundStatus.WAIT_SELLER.getCode())
                .le(Refund::getAutoAgreeAt, now));
    }

    /** 定时：5 天未解决平台介入（待卖家且 platformAt <= now）。 */
    List<Refund> selectAutoPlatformDue(LocalDateTime now) {
        return refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getStatus, RefundStatus.WAIT_SELLER.getCode())
                .le(Refund::getPlatformAt, now));
    }

    Order selectOrderByOrderNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
    }

    // ===================== 写 =====================

    void insertRefund(Refund r) {
        refundMapper.insert(r);
    }

    /** 通用状态变更（拒绝/撤销/介入等），按主键更新。 */
    void updateById(Refund upd) {
        refundMapper.updateById(upd);
    }

    /**
     * P0-2 并发安全：CAS 将状态由 wait_seller 推进到 refunding，
     * 仅当当前确为 wait_seller 才返回 affected=1，否则 0（竞态失败方放弃出款）。
     */
    int casToRefunding(Long id) {
        Refund refunding = new Refund();
        refunding.setStatus(RefundStatus.REFUNDING.getCode());
        return refundMapper.update(refunding, new LambdaQueryWrapper<Refund>()
                .eq(Refund::getId, id)
                .eq(Refund::getStatus, RefundStatus.WAIT_SELLER.getCode()));
    }

    /** CAS 将状态由 refunding 推进到 refunded（出款成功后写回完成时间）。 */
    int casToRefunded(Long id, LocalDateTime refundAt) {
        Refund done = new Refund();
        done.setId(id);
        done.setStatus(RefundStatus.REFUNDED.getCode());
        done.setRefundAt(refundAt);
        return refundMapper.update(done, new LambdaQueryWrapper<Refund>()
                .eq(Refund::getId, id)
                .eq(Refund::getStatus, RefundStatus.REFUNDING.getCode()));
    }

    void insertFundFlow(FundFlow flow) {
        fundFlowMapper.insert(flow);
    }

    /** 退款完成后关闭关联订单（仅置状态为 CLOSED，按主键更新）。 */
    void closeOrder(Long orderId) {
        Order ou = new Order();
        ou.setId(orderId);
        ou.setStatus(OrderStatus.CLOSED.getCode());
        orderMapper.updateById(ou);
    }
}
