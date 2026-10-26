package com.idlefish.trade.marketing.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.marketing.activity.entity.ActivityRecord;
import com.idlefish.trade.marketing.activity.entity.ActivityReconciliation;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.marketing.activity.mapper.ActivityRecordMapper;
import com.idlefish.trade.marketing.activity.mapper.ActivityReconciliationMapper;
import com.idlefish.trade.marketing.activity.mapper.MarketingActivityMapper;
import com.idlefish.trade.marketing.activity.ActivityCore;
import com.idlefish.trade.marketing.activity.ActivityRiskGate;
import com.idlefish.trade.marketing.activity.vo.ActivityReconcileVO;
import com.idlefish.trade.marketing.activity.vo.MarketingActivityVO;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一活动内核服务（ITER-F08 / REQ-01、REQ-02、REQ-03、REQ-05、REQ-06、REQ-11、REQ-13、REQ-15）。
 * <p>
 * 所有玩法（砍价 / 抽奖 / 直播秒杀）必须经由本服务完成：库存原子扣减、限领校验、时间窗校验、
 * 风控钩子、幂等落库、补贴预算控制与对账流水。新玩法不得自行实现上述逻辑（REQ-01）。
 * <p>
 * 防超卖：库存经 {@code UPDATE ... SET stock = stock - n, sold_count = sold_count + n WHERE stock >= n}
 * 原子扣减（affected 行网关）；并发/重放经唯一索引 {@code (activity_id, idempotency_key)} + 幂等查询兜底（REQ-06）。
 */
@Service
public class ActivityKernelService {

    private static final String STOCK_KEY_PREFIX = "act:stock:";

    private final MarketingActivityMapper activityMapper;
    private final ActivityRecordMapper recordMapper;
    private final ActivityReconciliationMapper reconMapper;
    private final UserMapper userMapper;
    private final ActivityRiskGate riskGate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MetricsRegistry metrics;

    public ActivityKernelService(MarketingActivityMapper activityMapper, ActivityRecordMapper recordMapper,
                                 ActivityReconciliationMapper reconMapper, UserMapper userMapper,
                                 ActivityRiskGate riskGate, RedisTemplate<String, Object> redisTemplate,
                                 MetricsRegistry metrics) {
        this.activityMapper = activityMapper;
        this.recordMapper = recordMapper;
        this.reconMapper = reconMapper;
        this.userMapper = userMapper;
        this.riskGate = riskGate;
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
    }

    /** 加载活动并断言进行中（REQ-03）。 */
    public MarketingActivity loadActive(Long id, LocalDateTime now) {
        MarketingActivity a = activityMapper.selectById(id);
        ActivityCore.assertOngoing(a, now);
        return a;
    }

    /** 加载活动（不校验状态）。 */
    public MarketingActivity load(Long id) {
        MarketingActivity a = activityMapper.selectById(id);
        if (a == null) {
            throw new BizException(Code.ACTIVITY_NOT_FOUND);
        }
        return a;
    }

    /** 统计用户已参与次数（未取消）。 */
    public int countUserJoined(Long activityId, Long userId) {
        Long c = recordMapper.selectCount(new LambdaQueryWrapper<ActivityRecord>()
                .eq(ActivityRecord::getActivityId, activityId)
                .eq(ActivityRecord::getUserId, userId)
                .ne(ActivityRecord::getStatus, "CANCELLED"));
        return c == null ? 0 : c.intValue();
    }

    /** 是否参与过（用于前端按钮置灰）。 */
    public boolean hasJoined(Long activityId, Long userId) {
        return countUserJoined(activityId, userId) > 0;
    }

    /**
     * 统一参与入口（REQ-01、REQ-03、REQ-05、REQ-06、REQ-13、REQ-15）。
     * 顺序：风控 → 人群 → 时间窗 → 限领 → 库存原子扣减 → 幂等落库 → 补贴预算。
     * 并发/重放：先按幂等键查重，命中直接返回既有记录（仅生效一次，REQ-06）。
     */
    @Transactional
    public ActivityRecord participate(Long userId, String deviceId, Long activityId, String idempotencyKey,
                                      String bizId, int qty, long amountFen, long subsidyFen,
                                      Long couponId, String status, String extra) {
        MarketingActivity activity = load(activityId);
        User user = userId == null ? null : userMapper.selectById(userId);

        // REQ-05 风控钩子
        if (!riskGate.allow(userId, deviceId, activityId, activity.getKind())) {
            metrics.increment("activity.risk.blocked");
            throw new BizException(Code.ACTIVITY_RISK_BLOCKED);
        }
        // REQ-13 人群定向（可见性与参与同时生效）
        ActivityCore.assertAudience(activity, user, LocalDateTime.now());
        // REQ-03 时间窗
        ActivityCore.assertOngoing(activity, LocalDateTime.now());
        // REQ-03 限领
        ActivityCore.assertUserLimit(countUserJoined(activityId, userId),
                activity.getLimitPerUser() == null ? 0 : activity.getLimitPerUser());

        // REQ-06 幂等查重
        ActivityRecord existing = recordMapper.selectOne(new LambdaQueryWrapper<ActivityRecord>()
                .eq(ActivityRecord::getActivityId, activityId)
                .eq(ActivityRecord::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            return existing;
        }

        // REQ-03 库存原子扣减（防超卖网关）
        int rows = activityMapper.update(null, new LambdaUpdateWrapper<MarketingActivity>()
                .eq(MarketingActivity::getId, activityId)
                .ge(MarketingActivity::getStock, qty)
                .setSql("stock = stock - " + qty + ", sold_count = sold_count + " + qty));
        if (rows == 0) {
            metrics.increment("activity.stock.preempted");
            throw new BizException(Code.STOCK_NOT_ENOUGH, "活动库存不足");
        }

        // REQ-06 幂等落库（唯一索引兜底并发重放）
        ActivityRecord rec = new ActivityRecord();
        rec.setRecordNo(com.idlefish.trade.common.util.IdGenerator.activityRecordNo());
        rec.setActivityId(activityId);
        rec.setUserId(userId);
        rec.setKind(activity.getKind());
        rec.setBizId(bizId);
        rec.setIdempotencyKey(idempotencyKey);
        rec.setStatus(status);
        rec.setAmountFen(amountFen);
        rec.setSubsidyFen(subsidyFen);
        rec.setCouponId(couponId);
        rec.setDeviceId(deviceId);
        rec.setExtra(extra);
        try {
            recordMapper.insert(rec);
        } catch (DuplicateKeyException e) {
            // 并发重放：回滚本次库存扣减（事务内回滚），返回既有记录
            activityMapper.update(null, new LambdaUpdateWrapper<MarketingActivity>()
                    .eq(MarketingActivity::getId, activityId)
                    .setSql("stock = stock + " + qty + ", sold_count = GREATEST(0, sold_count - " + qty + ")"));
            ActivityRecord dup = recordMapper.selectOne(new LambdaQueryWrapper<ActivityRecord>()
                    .eq(ActivityRecord::getActivityId, activityId)
                    .eq(ActivityRecord::getIdempotencyKey, idempotencyKey));
            return dup == null ? rec : dup;
        }

        // REQ-15 补贴预算控制（原子累加 + 预算闸门）
        if (subsidyFen > 0) {
            addSubsidy(activity, subsidyFen);
        }
        metrics.increment("activity.join.success");
        return rec;
    }

    /** 补贴预算原子累加（REQ-15）：预算为 null 表示不限；否则必须 <= 预算，否则抛 SUBSIDY_BUDGET_EXCEEDED。 */
    private void addSubsidy(MarketingActivity activity, long subsidyFen) {
        if (activity.getSubsidyBudgetFen() == null) {
            activityMapper.update(null, new LambdaUpdateWrapper<MarketingActivity>()
                    .eq(MarketingActivity::getId, activity.getId())
                    .setSql("subsidy_used_fen = subsidy_used_fen + " + subsidyFen));
            return;
        }
        int rows = activityMapper.update(null, new LambdaUpdateWrapper<MarketingActivity>()
                .eq(MarketingActivity::getId, activity.getId())
                .apply("subsidy_used_fen + " + subsidyFen + " <= subsidy_budget_fen")
                .setSql("subsidy_used_fen = subsidy_used_fen + " + subsidyFen));
        if (rows == 0) {
            metrics.increment("activity.subsidy.budget_exceeded");
            throw new BizException(Code.SUBSIDY_BUDGET_EXCEEDED);
        }
    }

    /** 活动库存预热至缓存（REQ-02）；best-effort，缓存不可用不影响 DB 强一致。 */
    public void preheat(Long activityId) {
        MarketingActivity a = activityMapper.selectById(activityId);
        if (a == null || a.getStock() == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(STOCK_KEY_PREFIX + activityId, a.getStock());
        } catch (RuntimeException ignored) {
            // 降级：依赖 DB 强一致
        }
    }

    /** 定时预热所有进行中活动库存（REQ-02）。 */
    @Scheduled(fixedDelay = 60_000)
    public void preheatAll() {
        try {
            List<MarketingActivity> ongoing = activityMapper.selectList(new LambdaQueryWrapper<MarketingActivity>()
                    .eq(MarketingActivity::getStatus, "ONGOING"));
            for (MarketingActivity a : ongoing) {
                preheat(a.getId());
            }
        } catch (Exception ignored) {
            // 调度容错
        }
    }

    /**
     * 活动结算与对账（REQ-11、REQ-15）：回收未使用库存、核算补贴成本与亏损、写入对账流水。
     * 幂等：同一活动重复结算覆盖更新。
     */
    @Transactional
    public ActivityReconciliation reconcile(Long activityId) {
        MarketingActivity a = load(activityId);
        List<ActivityRecord> records = recordMapper.selectList(new LambdaQueryWrapper<ActivityRecord>()
                .eq(ActivityRecord::getActivityId, activityId));
        int stockSold = 0;
        long subsidyTotal = 0L;
        int participant = records.size();
        int drawCount = 0;
        int winCount = 0;
        long revenue = 0L;
        for (ActivityRecord r : records) {
            if ("CANCELLED".equals(r.getStatus()) || "LOST".equals(r.getStatus())) {
                continue;
            }
            stockSold++;
            if (r.getSubsidyFen() != null) {
                subsidyTotal += r.getSubsidyFen();
            }
            if ("LOTTERY".equals(a.getKind())) {
                drawCount++;
                if ("WON".equals(r.getStatus())) {
                    winCount++;
                }
            } else {
                drawCount = Math.max(drawCount, participant);
            }
            if ("WON".equals(r.getStatus()) || "QUALIFIED".equals(r.getStatus())
                    || "SETTLED".equals(r.getStatus())) {
                if (r.getAmountFen() != null) {
                    revenue += r.getAmountFen();
                }
            }
        }
        // 回收未使用库存：活动结束时剩余配额（未售出部分）记为回收量；活动置 ENDED 不再接受参与。
        int recycled = a.getStock() == null ? 0 : Math.max(0, a.getStock());
        long costTotal = (a.getLossFloorFen() == null ? 0 : a.getLossFloorFen()) * stockSold;
        long lossFen = subsidyTotal + Math.max(0L, costTotal - revenue);

        // 标记活动结束（库存不再变更；recycled 仅作对账展示，不影响后续核销）。
        activityMapper.update(null, Wrappers.<MarketingActivity>lambdaUpdate()
                .eq(MarketingActivity::getId, activityId)
                .set(MarketingActivity::getStatus, "ENDED"));

        ActivityReconciliation recon = reconMapper.selectOne(new LambdaQueryWrapper<ActivityReconciliation>()
                .eq(ActivityReconciliation::getActivityId, activityId));
        boolean insert = recon == null;
        if (insert) {
            recon = new ActivityReconciliation();
            recon.setActivityId(activityId);
        }
        recon.setKind(a.getKind());
        recon.setStockTotal(a.getStock() == null ? 0 : a.getStock());
        recon.setStockSold(stockSold);
        recon.setStockRecycled(recycled);
        recon.setSubsidyTotalFen(subsidyTotal);
        recon.setParticipantCount(participant);
        recon.setDrawCount(drawCount);
        recon.setWinCount(winCount);
        recon.setLossFen(lossFen);
        recon.setStatus("SETTLED");
        recon.setSettledAt(LocalDateTime.now());
        if (insert) {
            reconMapper.insert(recon);
        } else {
            reconMapper.updateById(recon);
        }
        return recon;
    }

    /** 对账 → VO。 */
    public ActivityReconcileVO toReconcileVO(Long activityId) {
        MarketingActivity a = load(activityId);
        ActivityReconciliation recon = reconMapper.selectOne(new LambdaQueryWrapper<ActivityReconciliation>()
                .eq(ActivityReconciliation::getActivityId, activityId));
        ActivityReconcileVO vo = new ActivityReconcileVO();
        vo.setActivityId(activityId);
        vo.setKind(a.getKind());
        vo.setTitle(a.getTitle());
        if (recon != null) {
            vo.setStockTotal(recon.getStockTotal());
            vo.setStockSold(recon.getStockSold());
            vo.setStockRecycled(recon.getStockRecycled());
            vo.setSubsidyTotalFen(recon.getSubsidyTotalFen());
            vo.setParticipantCount(recon.getParticipantCount());
            vo.setDrawCount(recon.getDrawCount());
            vo.setWinCount(recon.getWinCount());
            vo.setLossFen(recon.getLossFen());
            vo.setStatus(recon.getStatus());
            vo.setSettledAt(recon.getSettledAt());
        }
        return vo;
    }

    /** 用户端活动列表（仅进行中 + 人群定向可见，REQ-13）。 */
    public List<MarketingActivityVO> listActivities(String kind, Long userId) {
        User user = userId == null ? null : userMapper.selectById(userId);
        LambdaQueryWrapper<MarketingActivity> w = new LambdaQueryWrapper<MarketingActivity>()
                .eq(MarketingActivity::getStatus, "ONGOING")
                .orderByDesc(MarketingActivity::getCreatedAt);
        if (kind != null && !kind.isBlank()) {
            w.eq(MarketingActivity::getKind, kind);
        }
        List<MarketingActivity> all = activityMapper.selectList(w);
        List<MarketingActivityVO> out = new ArrayList<>();
        for (MarketingActivity a : all) {
            try {
                ActivityCore.assertAudience(a, user, LocalDateTime.now());
            } catch (BizException e) {
                continue; // 人群不可见则不在列表透出
            }
            out.add(toVO(a, userId));
        }
        return out;
    }

    /** 活动详情（含当前用户是否已参与）。 */
    public MarketingActivityVO getActivity(Long id, Long userId) {
        MarketingActivity a = load(id);
        return toVO(a, userId);
    }

    /** 我的参与记录。 */
    public List<ActivityRecord> myRecords(Long userId) {
        return recordMapper.selectList(new LambdaQueryWrapper<ActivityRecord>()
                .eq(ActivityRecord::getUserId, userId)
                .orderByDesc(ActivityRecord::getCreatedAt));
    }

    /** 指定时间以来的参与记录数（监控用）。 */
    public long myRecordsCountSince(LocalDateTime since) {
        Long c = recordMapper.selectCount(new LambdaQueryWrapper<ActivityRecord>()
                .ge(ActivityRecord::getCreatedAt, since));
        return c == null ? 0 : c;
    }

    /** 实体 → VO（携带 joined 标志）。 */
    public MarketingActivityVO toVO(MarketingActivity a, Long userId) {
        MarketingActivityVO vo = new MarketingActivityVO();
        vo.setId(a.getId());
        vo.setKind(a.getKind());
        vo.setTitle(a.getTitle());
        vo.setItemId(a.getItemId());
        vo.setActivityPrice(a.getActivityPrice());
        vo.setStock(a.getStock());
        vo.setSoldCount(a.getSoldCount());
        vo.setLimitPerUser(a.getLimitPerUser());
        vo.setStatus(a.getStatus());
        vo.setStartAt(a.getStartAt());
        vo.setEndAt(a.getEndAt());
        vo.setAudienceType(a.getAudienceType());
        vo.setRiskLevel(a.getRiskLevel());
        vo.setSubsidyBudgetFen(a.getSubsidyBudgetFen());
        vo.setSubsidyUsedFen(a.getSubsidyUsedFen());
        vo.setPlayConfig(a.getPlayConfig());
        vo.setJoined(userId != null && hasJoined(a.getId(), userId));
        return vo;
    }

    /** 运营后台：全部活动（含草稿/已结束）。 */
    public List<MarketingActivity> listAll() {
        return activityMapper.selectList(new LambdaQueryWrapper<MarketingActivity>()
                .orderByDesc(MarketingActivity::getCreatedAt));
    }

    /** 发布活动（DRAFT → ONGOING）并预热缓存（REQ-02）。 */
    @Transactional
    public MarketingActivity publish(Long id) {
        MarketingActivity a = load(id);
        if (!"DRAFT".equals(a.getStatus())) {
            throw new BizException(Code.STATE_NOT_ALLOWED, "仅草稿态活动可发布");
        }
        MarketingActivity upd = new MarketingActivity();
        upd.setId(id);
        upd.setStatus("ONGOING");
        activityMapper.updateById(upd);
        preheat(id);
        metrics.increment("activity.publish");
        return load(id);
    }
}
