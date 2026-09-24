package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.enums.ReviewRole;
import com.idlefish.trade.item.service.ContentAuditService;
import com.idlefish.trade.trade.dto.ReviewSubmitDTO;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.Review;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.ReviewMapper;
import com.idlefish.trade.trade.vo.ReviewVO;
import com.idlefish.trade.user.service.CreditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评价服务（F-06）：订单完成后买卖双方互评，内容机审后入库；审核通过触发被评价方信用重算。
 */
@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final ContentAuditService contentAuditService;
    private final CreditService creditService;

    public ReviewService(ReviewMapper reviewMapper, OrderMapper orderMapper,
                         ContentAuditService contentAuditService, CreditService creditService) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.contentAuditService = contentAuditService;
        this.creditService = creditService;
    }

    /** 提交评价：校验订单已完成、评价人是交易一方、同端同单幂等、内容机审。返回评价 ID。 */
    @Transactional
    public Long submit(Long reviewerId, ReviewSubmitDTO dto) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, dto.getOrderNo()));
        if (o == null) {
            throw new BizException(Code.ORDER_NOT_FOUND);
        }
        if (!o.getBuyerId().equals(reviewerId) && !o.getSellerId().equals(reviewerId)) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "您不是该订单的交易方，无法评价");
        }
        if (!"COMPLETED".equals(o.getStatus()) && !"CLOSED".equals(o.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅已完成/已关闭订单可评价");
        }
        ReviewRole role = o.getBuyerId().equals(reviewerId) ? ReviewRole.BUYER_SELLER : ReviewRole.SELLER_BUYER;
        Long targetId = o.getBuyerId().equals(reviewerId) ? o.getSellerId() : o.getBuyerId();

        // 幂等：同订单、同角色仅允许一条（待审/通过）
        Review exist = reviewMapper.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderNo, dto.getOrderNo())
                .eq(Review::getRole, role.getCode())
                .in(Review::getStatus, List.of(0, 1)));
        if (exist != null) {
            return exist.getId();
        }

        boolean auditPass = contentAuditService.auditText(dto.getContent()).isPass();

        Review r = new Review();
        r.setOrderNo(dto.getOrderNo());
        r.setItemId(o.getItemId());
        r.setReviewerId(reviewerId);
        r.setTargetId(targetId);
        r.setRole(role.getCode());
        r.setRating(dto.getRating());
        r.setContent(dto.getContent());
        r.setAnonymous(dto.getAnonymous() != null && dto.getAnonymous() == 1 ? 1 : 0);
        // 机审通过直接入库通过；机审拦截置驳回（可由管理员复核）
        r.setStatus(auditPass ? 1 : 2);
        if (!auditPass) {
            r.setRejectReason("内容机审未通过");
        }
        reviewMapper.insert(r);

        if (auditPass) {
            creditService.recompute(targetId);
        }
        return r.getId();
    }

    /** 商品维度评价展示（仅通过）。 */
    public List<ReviewVO> listByItem(Long itemId) {
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                        .eq(Review::getItemId, itemId).eq(Review::getStatus, 1)
                        .orderByDesc(Review::getCreatedAt))
                .stream().map(ReviewVO::from).toList();
    }

    /** 我发出的评价（仅通过）。 */
    public List<ReviewVO> myReviews(Long reviewerId) {
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                        .eq(Review::getReviewerId, reviewerId).eq(Review::getStatus, 1)
                        .orderByDesc(Review::getCreatedAt))
                .stream().map(ReviewVO::from).toList();
    }

    /** 我收到的评价（仅通过，被评价方视角）。 */
    public List<ReviewVO> received(Long targetId) {
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                        .eq(Review::getTargetId, targetId).eq(Review::getStatus, 1)
                        .orderByDesc(Review::getCreatedAt))
                .stream().map(ReviewVO::from).toList();
    }

    /** 待审核评价（管理端）。 */
    public List<ReviewVO> pendingList() {
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                        .eq(Review::getStatus, 0).orderByDesc(Review::getCreatedAt))
                .stream().map(ReviewVO::from).toList();
    }

    /** 审核通过（管理端）→ 触发被评价方信用重算。 */
    @Transactional
    public void approve(Long id) {
        Review r = get(id);
        if (r.getStatus() == 1) {
            return;
        }
        Review upd = new Review();
        upd.setId(id);
        upd.setStatus(1);
        reviewMapper.updateById(upd);
        creditService.recompute(r.getTargetId());
    }

    /** 审核驳回（管理端）。 */
    @Transactional
    public void reject(Long id, String reason) {
        Review r = get(id);
        if (r.getStatus() == 2) {
            return;
        }
        Review upd = new Review();
        upd.setId(id);
        upd.setStatus(2);
        upd.setRejectReason(reason);
        reviewMapper.updateById(upd);
    }

    private Review get(Long id) {
        Review r = reviewMapper.selectById(id);
        if (r == null) {
            throw new BizException(Code.NOT_FOUND, "评价不存在");
        }
        return r;
    }
}
