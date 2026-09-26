package com.idlefish.trade.marketing.invite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.marketing.invite.InviteValidator;
import com.idlefish.trade.marketing.invite.entity.InviteCode;
import com.idlefish.trade.marketing.invite.entity.InviteRelation;
import com.idlefish.trade.marketing.invite.mapper.InviteCodeMapper;
import com.idlefish.trade.marketing.invite.mapper.InviteRelationMapper;
import com.idlefish.trade.marketing.service.CouponService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

/**
 * 邀请拉新 / 分销服务（F-13.4）：邀请码生成、绑定关系、自邀自风控、首单返券奖励。
 * 绑定经唯一约束 (inviter_id, invitee_id) 兜底并发；返券经 {@link CouponService#grantCoupon} 直接发放。
 */
@Service
public class InviteService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final InviteCodeMapper codeMapper;
    private final InviteRelationMapper relationMapper;
    private final CouponService couponService;
    private final IdlefishProperties props;
    private final MetricsRegistry metrics;

    public InviteService(InviteCodeMapper codeMapper, InviteRelationMapper relationMapper,
                         CouponService couponService, IdlefishProperties props, MetricsRegistry metrics) {
        this.codeMapper = codeMapper;
        this.relationMapper = relationMapper;
        this.couponService = couponService;
        this.props = props;
        this.metrics = metrics;
    }

    /** 获取或生成我的邀请码（每个用户唯一）。 */
    @Transactional(rollbackFor = Exception.class)
    public String ensureCode(Long userId) {
        InviteCode existing = codeMapper.selectOne(
                new LambdaQueryWrapper<InviteCode>().eq(InviteCode::getUserId, userId));
        if (existing != null) {
            return existing.getCode();
        }
        InviteCode ic = new InviteCode();
        ic.setUserId(userId);
        ic.setCode(generateCode(userId));
        try {
            codeMapper.insert(ic);
        } catch (DuplicateKeyException e) {
            // 极小概率碰撞（userId 维度唯一约束），查回已有记录
            InviteCode again = codeMapper.selectOne(
                    new LambdaQueryWrapper<InviteCode>().eq(InviteCode::getUserId, userId));
            return again != null ? again.getCode() : ic.getCode();
        }
        return ic.getCode();
    }

    /** 绑定邀请关系（校验邀请码有效性 / 自邀自拦截 / 重复绑定拦截）。 */
    @Transactional(rollbackFor = Exception.class)
    public InviteRelation bind(Long inviteeId, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BizException(Code.INVITE_CODE_NOT_FOUND, "邀请码不能为空");
        }
        String code = rawCode.trim().toUpperCase();
        InviteCode ic = codeMapper.selectOne(
                new LambdaQueryWrapper<InviteCode>().eq(InviteCode::getCode, code));
        Long inviterId = InviteValidator.resolveInviter(ic, inviteeId);
        InviteRelation existing = relationMapper.selectOne(new LambdaQueryWrapper<InviteRelation>()
                .eq(InviteRelation::getInviterId, inviterId)
                .eq(InviteRelation::getInviteeId, inviteeId));
        InviteValidator.assertNotBound(existing);

        InviteRelation r = new InviteRelation();
        r.setInviterId(inviterId);
        r.setInviteeId(inviteeId);
        r.setRewarded(0);
        relationMapper.insert(r);
        metrics.increment("invite.bind");
        return r;
    }

    /** 首单返券：被邀请人首单支付成功 → 邀请人得返券奖励（best-effort，不阻断订单主流程）。 */
    public void rewardFirstOrder(Long inviteeId) {
        if (!props.getInvite().isEnabled()) {
            return;
        }
        Long rewardCouponId = props.getInvite().getRewardCouponId();
        if (rewardCouponId == null) {
            return;
        }
        InviteRelation r = relationMapper.selectOne(
                new LambdaQueryWrapper<InviteRelation>().eq(InviteRelation::getInviteeId, inviteeId));
        if (r == null || (r.getRewarded() != null && r.getRewarded() == 1)) {
            return;
        }
        try {
            couponService.grantCoupon(r.getInviterId(), rewardCouponId);
            r.setRewarded(1);
            relationMapper.updateById(r);
            metrics.increment("invite.reward");
        } catch (Exception ignored) {
            // 返券失败不影响订单主流程
        }
    }

    /** 我的邀请列表（按时间倒序）。 */
    public List<InviteRelation> myInvitees(Long inviterId) {
        return relationMapper.selectList(new LambdaQueryWrapper<InviteRelation>()
                .eq(InviteRelation::getInviterId, inviterId)
                .orderByDesc(InviteRelation::getCreatedAt));
    }

    private String generateCode(Long userId) {
        String base = "INV" + Long.toString(userId == null ? 0 : userId, 36).toUpperCase();
        StringBuilder sb = new StringBuilder(base);
        Random rnd = new Random();
        for (int i = 0; i < 4; i++) {
            sb.append(CODE_CHARS.charAt(rnd.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
