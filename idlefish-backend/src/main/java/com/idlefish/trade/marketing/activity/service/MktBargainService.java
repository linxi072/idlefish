package com.idlefish.trade.marketing.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.marketing.activity.ActivityKind;
import com.idlefish.trade.marketing.activity.BargainCalculator;
import com.idlefish.trade.marketing.activity.BargainConfig;
import com.idlefish.trade.marketing.activity.entity.ActivityRecord;
import com.idlefish.trade.marketing.activity.entity.BargainAssist;
import com.idlefish.trade.marketing.activity.entity.BargainSession;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.marketing.activity.mapper.BargainAssistMapper;
import com.idlefish.trade.marketing.activity.mapper.BargainSessionMapper;
import com.idlefish.trade.marketing.activity.vo.BargainAssistVO;
import com.idlefish.trade.marketing.activity.vo.BargainSessionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 砍价玩法服务（ITER-F08 / REQ-08）。
 * <p>
 * 复用统一活动内核完成「场次配额占用 / 风控 / 人群 / 限领 / 幂等」（{@link ActivityKernelService#participate}），
 * 本服务仅负责砍价特有的「逐刀金额计算 + 成团判定 + 下单资格生成」。
 * 砍价额计算为纯函数（{@link BargainCalculator}，REQ-12），总额受控不会砍穿底价（REQ-03/REQ-14）。
 */
@Service
public class MktBargainService {

    private static final Random RND = new Random();

    private final ActivityKernelService kernel;
    private final BargainSessionMapper sessionMapper;
    private final BargainAssistMapper assistMapper;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metrics;

    public MktBargainService(ActivityKernelService kernel, BargainSessionMapper sessionMapper,
                          BargainAssistMapper assistMapper, ObjectMapper objectMapper, MetricsRegistry metrics) {
        this.kernel = kernel;
        this.sessionMapper = sessionMapper;
        this.assistMapper = assistMapper;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    private BargainConfig parse(MarketingActivity a) {
        if (a.getPlayConfig() == null) {
            throw new BizException(Code.BIZ_ERROR, "砍价配置缺失");
        }
        try {
            return objectMapper.readValue(a.getPlayConfig(), BargainConfig.class);
        } catch (Exception e) {
            throw new BizException(Code.BIZ_ERROR, "砍价配置解析失败");
        }
    }

    /** 发起砍价（占用内核 1 个场次配额）。 */
    @Transactional
    public BargainSessionVO createSession(Long userId, String deviceId, Long activityId) {
        MarketingActivity a = kernel.load(activityId);
        if (!ActivityKind.BARGAIN.name().equals(a.getKind())) {
            throw new BizException(Code.ACTIVITY_KIND_NOT_SUPPORTED);
        }
        BargainConfig cfg = parse(a);
        if (cfg.getOriginPriceFen() == null || cfg.getFloorPriceFen() == null) {
            throw new BizException(Code.BIZ_ERROR, "砍价原价或底价未配置");
        }
        String sessionNo = IdGenerator.bargainSessionNo();
        // 内核参与：占用 1 个活动库存（砍价场次配额），完成风控/人群/限领/幂等（REQ-01/05/06/13）
        ActivityRecord rec = kernel.participate(userId, deviceId, activityId,
                "bargain:" + userId + ":" + activityId, sessionNo, 1, 0, 0, null, "JOINED", null);
        BargainSession s = new BargainSession();
        s.setSessionNo(sessionNo);
        s.setActivityId(activityId);
        s.setInitiatorUserId(userId);
        s.setRecordNo(rec.getRecordNo());
        s.setCurrentPriceFen(cfg.getOriginPriceFen());
        s.setFloorPriceFen(cfg.getFloorPriceFen());
        s.setTargetAssistCount(cfg.getTargetAssistCount());
        s.setAssistCount(0);
        s.setStatus("ONGOING");
        s.setExpireAt(a.getEndAt());
        sessionMapper.insert(s);
        metrics.increment("bargain.session.create");
        return toVO(s, cfg.getOriginPriceFen());
    }

    /** 好友助力（纯函数计算本次砍价额，并更新会话状态）。 */
    @Transactional
    public BargainAssistVO assist(String sessionNo, Long assistUserId, String deviceId) {
        BargainSession s = sessionMapper.selectOne(new LambdaQueryWrapper<BargainSession>()
                .eq(BargainSession::getSessionNo, sessionNo));
        if (s == null) {
            throw new BizException(Code.BIZ_ERROR, "砍价会话不存在");
        }
        if ("QUALIFIED".equals(s.getStatus())) {
            throw new BizException(Code.BARGAIN_TARGET_REACHED);
        }
        if ("EXPIRED".equals(s.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "砍价已过期");
        }
        LocalDateTime now = LocalDateTime.now();
        if (s.getExpireAt() != null && now.isAfter(s.getExpireAt())) {
            s.setStatus("EXPIRED");
            sessionMapper.updateById(s);
            throw new BizException(Code.STATE_NOT_ALLOWED, "砍价已过期");
        }
        if (assistUserId != null && assistUserId.equals(s.getInitiatorUserId())) {
            throw new BizException(Code.BIZ_ERROR, "不能为自己砍价");
        }
        // 幂等：同一用户对同一会话仅助力一次（唯一索引兜底）
        Long done = assistMapper.selectCount(new LambdaQueryWrapper<BargainAssist>()
                .eq(BargainAssist::getSessionId, s.getId())
                .eq(BargainAssist::getAssistUserId, assistUserId));
        if (done != null && done > 0) {
            throw new BizException(Code.BIZ_ERROR, "您已为该砍价助力过");
        }
        MarketingActivity a = kernel.load(s.getActivityId());
        BargainConfig cfg = parse(a);
        long cut = BargainCalculator.calcNextCut(s.getCurrentPriceFen(), s.getFloorPriceFen(),
                s.getAssistCount(), cfg, RND);
        long newPrice = BargainCalculator.calcFinalPrice(s.getCurrentPriceFen(), cut);
        s.setCurrentPriceFen(newPrice);
        s.setAssistCount(s.getAssistCount() + 1);
        boolean floorReached = BargainCalculator.isFloorReached(newPrice, s.getFloorPriceFen());
        boolean targetReached = s.getAssistCount() >= s.getTargetAssistCount();
        boolean qualified = floorReached || targetReached;
        if (qualified) {
            s.setStatus("QUALIFIED");
            s.setQualifyExpireAt(now.plusHours(24));
            metrics.increment("bargain.qualified");
        }
        sessionMapper.updateById(s);
        BargainAssist ass = new BargainAssist();
        ass.setSessionId(s.getId());
        ass.setSessionNo(sessionNo);
        ass.setAssistUserId(assistUserId);
        ass.setDeviceId(deviceId);
        ass.setAmountFen(cut);
        assistMapper.insert(ass);
        metrics.increment("bargain.assist");
        BargainAssistVO vo = new BargainAssistVO();
        vo.setSessionNo(sessionNo);
        vo.setCutFen(cut);
        vo.setCurrentPriceFen(newPrice);
        vo.setQualified(qualified);
        vo.setStatus(s.getStatus());
        return vo;
    }

    /** 查询砍价会话（含进度）。 */
    public BargainSessionVO getSession(String sessionNo) {
        BargainSession s = sessionMapper.selectOne(new LambdaQueryWrapper<BargainSession>()
                .eq(BargainSession::getSessionNo, sessionNo));
        if (s == null) {
            throw new BizException(Code.BIZ_ERROR, "砍价会话不存在");
        }
        MarketingActivity a = kernel.load(s.getActivityId());
        BargainConfig cfg = parse(a);
        return toVO(s, cfg.getOriginPriceFen());
    }

    private BargainSessionVO toVO(BargainSession s, Long originPriceFen) {
        BargainSessionVO vo = new BargainSessionVO();
        vo.setSessionNo(s.getSessionNo());
        vo.setActivityId(s.getActivityId());
        vo.setInitiatorUserId(s.getInitiatorUserId());
        vo.setCurrentPriceFen(s.getCurrentPriceFen());
        vo.setFloorPriceFen(s.getFloorPriceFen());
        vo.setOriginPriceFen(originPriceFen);
        vo.setTargetAssistCount(s.getTargetAssistCount());
        vo.setAssistCount(s.getAssistCount());
        vo.setStatus(s.getStatus());
        vo.setExpireAt(s.getExpireAt());
        vo.setQualifyExpireAt(s.getQualifyExpireAt());
        int target = s.getTargetAssistCount() == null ? 0 : s.getTargetAssistCount();
        vo.setRemainAssist(Math.max(0, target - s.getAssistCount()));
        long origin = originPriceFen == null ? 0 : originPriceFen;
        long cur = s.getCurrentPriceFen() == null ? 0 : s.getCurrentPriceFen();
        vo.setCutTotalFen(Math.max(0, origin - cur));
        return vo;
    }
}
