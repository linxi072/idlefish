package com.idlefish.trade.marketing.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.marketing.activity.ActivityCore;
import com.idlefish.trade.marketing.activity.ActivityKind;
import com.idlefish.trade.marketing.activity.LotteryDrawer;
import com.idlefish.trade.marketing.activity.LotteryPrize;
import com.idlefish.trade.marketing.activity.entity.ActivityRecord;
import com.idlefish.trade.marketing.activity.entity.LotteryPrizeEntity;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.marketing.activity.mapper.ActivityRecordMapper;
import com.idlefish.trade.marketing.activity.mapper.LotteryPrizeMapper;
import com.idlefish.trade.marketing.activity.vo.ActivityJoinResultVO;
import com.idlefish.trade.marketing.service.CouponService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 抽奖玩法服务（ITER-F08 / REQ-09、REQ-10、REQ-06）。
 * <p>
 * 复用统一活动内核完成「名额占用 / 风控 / 人群 / 限领 / 幂等」（{@link ActivityKernelService#participate}）。
 * 本服务负责抽奖特有的「概率抽取（纯函数 {@link LotteryDrawer}）+ 奖品原子扣减（防超发）+ 高价值实名（REQ-10）」。
 * 概率配置留痕于参与记录 {@code extra}（REQ-09 可审计）。
 */
@Service
public class LotteryService {

    private static final Random RND = new Random();
    /** 高价值阈值（分）：≥ 该值视为高价值，需补充实名与收货信息（REQ-10）。 */
    private static final long HIGH_VALUE_FEN = 10_000L;

    private final ActivityKernelService kernel;
    private final LotteryPrizeMapper prizeMapper;
    private final ActivityRecordMapper recordMapper;
    private final CouponService couponService;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metrics;

    public LotteryService(ActivityKernelService kernel, LotteryPrizeMapper prizeMapper,
                          ActivityRecordMapper recordMapper, CouponService couponService,
                          ObjectMapper objectMapper, MetricsRegistry metrics) {
        this.kernel = kernel;
        this.prizeMapper = prizeMapper;
        this.recordMapper = recordMapper;
        this.couponService = couponService;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    /** 抽奖（占用 1 名额 + 概率抽取 + 奖品原子扣减）。 */
    @Transactional
    public ActivityJoinResultVO draw(Long userId, String deviceId, Long activityId) {
        MarketingActivity a = kernel.load(activityId);
        if (!ActivityKind.LOTTERY.name().equals(a.getKind())) {
            throw new BizException(Code.ACTIVITY_KIND_NOT_SUPPORTED);
        }
        List<LotteryPrizeEntity> entities = prizeMapper.selectList(new LambdaQueryWrapper<LotteryPrizeEntity>()
                .eq(LotteryPrizeEntity::getActivityId, activityId)
                .eq(LotteryPrizeEntity::getStatus, "ACTIVE"));
        if (entities.isEmpty()) {
            throw new BizException(Code.BIZ_ERROR, "抽奖奖品未配置");
        }
        List<LotteryPrize> prizes = toPrizes(entities);
        if (!LotteryDrawer.validateProbabilities(prizes)) {
            throw new BizException(Code.BIZ_ERROR, "抽奖概率配置非法（总和需 ≤ 1）");
        }
        // 内核参与：占用 1 个抽奖名额（活动库存）
        ActivityRecord rec = kernel.participate(userId, deviceId, activityId,
                "lottery:" + userId + ":" + activityId, "draw:" + activityId, 1, 0, 0, null, "PENDING_DRAW", null);
        double r = RND.nextDouble();
        LotteryPrize picked = LotteryDrawer.draw(prizes, r);
        String snapshot = buildSnapshot(r, prizes, picked == null ? null : picked.getPrizeId(),
                picked == null ? false : false);
        if (picked == null) {
            rec.setStatus("LOST");
            rec.setExtra(snapshot);
            recordMapper.updateById(rec);
            metrics.increment("lottery.draw.miss");
            return toResult(rec, false, false);
        }
        // 中奖：原子扣减奖品库存（防超发 REQ-06）
        LotteryPrizeEntity entity = entityByPrizeId(entities, picked.getPrizeId());
        int rows = prizeMapper.update(null, new LambdaUpdateWrapper<LotteryPrizeEntity>()
                .eq(LotteryPrizeEntity::getId, entity.getId())
                .ge(LotteryPrizeEntity::getStock, 1)
                .setSql("stock = stock - 1"));
        if (rows == 0) {
            // 奖品售罄兜底：本次判为未中奖（库存安全优先）
            rec.setStatus("LOST");
            rec.setExtra(buildSnapshot(r, prizes, picked.getPrizeId(), true));
            recordMapper.updateById(rec);
            metrics.increment("lottery.draw.soldout");
            return toResult(rec, false, false);
        }
        rec.setAmountFen(picked.getValueFen());
        boolean realNameReq = "PHYSICAL".equals(picked.getType())
                || (picked.getValueFen() != null && picked.getValueFen() >= HIGH_VALUE_FEN);
        if (realNameReq) {
            rec.setStatus("PENDING_REALNAME");
        } else {
            rec.setStatus("WON");
            if ("COUPON".equals(picked.getType()) && picked.getCouponTemplateId() != null) {
                try {
                    couponService.grantCoupon(userId, picked.getCouponTemplateId());
                } catch (Exception ignored) {
                    metrics.increment("lottery.coupon.grant_fail");
                }
            }
        }
        rec.setExtra(buildSnapshot(r, prizes, picked.getPrizeId(), false));
        recordMapper.updateById(rec);
        metrics.increment("lottery.draw.won");
        return toResult(rec, true, "PENDING_REALNAME".equals(rec.getStatus()));
    }

    /** 高价值/实物奖品补充实名与收货信息（REQ-10）。 */
    @Transactional
    public ActivityJoinResultVO submitRealname(String recordNo, Long userId, String realName,
                                               String idNo, String address) {
        ActivityRecord rec = recordMapper.selectOne(new LambdaQueryWrapper<ActivityRecord>()
                .eq(ActivityRecord::getRecordNo, recordNo));
        if (rec == null || !ActivityKind.LOTTERY.name().equals(rec.getKind())) {
            throw new BizException(Code.BIZ_ERROR, "抽奖记录不存在");
        }
        if (!userId.equals(rec.getUserId())) {
            throw new BizException(Code.FORBIDDEN);
        }
        if (!"PENDING_REALNAME".equals(rec.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "当前状态无需补充实名信息");
        }
        if (isBlank(realName) || isBlank(idNo) || isBlank(address)) {
            throw new BizException(Code.PARAM_MISSING, "姓名、证件号与收货地址均必填");
        }
        // 敏感信息最小化：仅留存脱敏证件号尾号 + 收货信息（REQ-10 最小必要）
        String maskedId = idNo.length() > 4 ? idNo.substring(0, idNo.length() - 4) + "****" : "****";
        Map<String, Object> extra = parseExtra(rec.getExtra());
        extra.put("realName", realName);
        extra.put("idNoMasked", maskedId);
        extra.put("address", address);
        extra.put("realnameProvided", true);
        rec.setExtra(writeExtra(extra));
        rec.setStatus("WON");
        recordMapper.updateById(rec);
        metrics.increment("lottery.realname.submit");
        return toResult(rec, true, false);
    }

    private List<LotteryPrize> toPrizes(List<LotteryPrizeEntity> entities) {
        List<LotteryPrize> list = new ArrayList<>();
        for (LotteryPrizeEntity e : entities) {
            LotteryPrize p = new LotteryPrize();
            p.setPrizeId(e.getPrizeId());
            p.setName(e.getName());
            p.setType(e.getType());
            p.setValueFen(e.getValueFen());
            p.setProbability(e.getProbability());
            p.setStock(e.getStock() == null ? 0 : e.getStock());
            p.setRealNameRequired("PHYSICAL".equals(e.getType())
                    || (e.getValueFen() != null && e.getValueFen() >= HIGH_VALUE_FEN));
            p.setCouponTemplateId(e.getCouponTemplateId());
            list.add(p);
        }
        return list;
    }

    private LotteryPrizeEntity entityByPrizeId(List<LotteryPrizeEntity> entities, String prizeId) {
        for (LotteryPrizeEntity e : entities) {
            if (e.getPrizeId() != null && e.getPrizeId().equals(prizeId)) {
                return e;
            }
        }
        throw new BizException(Code.BIZ_ERROR, "抽奖奖品不存在");
    }

    /** 审计快照：留存随机种子与概率配置（REQ-09 可审计）。 */
    private String buildSnapshot(double r, List<LotteryPrize> prizes, String pickedId, boolean soldOut) {
        Map<String, Object> m = new HashMap<>();
        m.put("r", r);
        List<Map<String, Object>> ps = new ArrayList<>();
        for (LotteryPrize p : prizes) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("prizeId", p.getPrizeId());
            pm.put("p", p.getProbability() == null ? BigDecimal.ZERO : p.getProbability());
            ps.add(pm);
        }
        m.put("prizes", ps);
        m.put("picked", pickedId);
        m.put("soldOut", soldOut);
        return writeExtra(m);
    }

    private Map<String, Object> parseExtra(String extra) {
        if (extra == null || extra.isBlank()) {
            return new HashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(extra, Map.class);
            return m;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String writeExtra(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ActivityJoinResultVO toResult(ActivityRecord rec, boolean won, boolean pendingRealname) {
        ActivityJoinResultVO vo = new ActivityJoinResultVO();
        vo.setRecordNo(rec.getRecordNo());
        vo.setActivityId(rec.getActivityId());
        vo.setKind(rec.getKind());
        vo.setStatus(rec.getStatus());
        vo.setAmountFen(rec.getAmountFen());
        vo.setCouponId(rec.getCouponId());
        vo.setWon(won);
        vo.setExtra(rec.getExtra());
        return vo;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 运营配置/更新抽奖奖品池（原子清空重插，幂等；概率配置留痕于实体，REQ-09）。 */
    @Transactional
    public void savePrizes(Long activityId, List<LotteryPrize> prizes) {
        if (prizes == null || prizes.isEmpty()) {
            throw new BizException(Code.BIZ_ERROR, "抽奖奖品为空");
        }
        if (!LotteryDrawer.validateProbabilities(prizes)) {
            throw new BizException(Code.BIZ_ERROR, "抽奖概率配置非法（总和需 ≤ 1）");
        }
        // 亏损风险阻断（REQ-14）：奖品价值低于成本线则阻断配置
        MarketingActivity activity = kernel.load(activityId);
        long costFen = activity.getLossFloorFen() == null ? 0 : activity.getLossFloorFen();
        if (costFen > 0) {
            for (LotteryPrize p : prizes) {
                ActivityCore.assertLossSafe(p.getValueFen() == null ? 0 : p.getValueFen(), costFen);
            }
        }
        prizeMapper.delete(new LambdaQueryWrapper<LotteryPrizeEntity>()
                .eq(LotteryPrizeEntity::getActivityId, activityId));
        for (LotteryPrize p : prizes) {
            LotteryPrizeEntity e = new LotteryPrizeEntity();
            e.setActivityId(activityId);
            e.setPrizeId(p.getPrizeId());
            e.setName(p.getName());
            e.setType(p.getType());
            e.setValueFen(p.getValueFen());
            e.setProbability(p.getProbability());
            e.setStock(p.getStock());
            e.setCouponTemplateId(p.getCouponTemplateId());
            e.setStatus("ACTIVE");
            prizeMapper.insert(e);
        }
        metrics.increment("lottery.prizes.configured");
    }
}
