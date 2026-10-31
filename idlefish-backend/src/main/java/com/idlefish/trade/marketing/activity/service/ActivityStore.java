package com.idlefish.trade.marketing.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.idlefish.trade.marketing.activity.entity.ActivityRecord;
import com.idlefish.trade.marketing.activity.entity.ActivityReconciliation;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.marketing.activity.mapper.ActivityRecordMapper;
import com.idlefish.trade.marketing.activity.mapper.ActivityReconciliationMapper;
import com.idlefish.trade.marketing.activity.mapper.MarketingActivityMapper;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动内核数据访问层：持有活动/记录/对账/用户 Mapper 与库存缓存，对外仅暴露读写入与原子 CAS。
 * 拆分自 {@link ActivityKernelService}（ITER-F08），与 CouponStore / PricingStore / RefundStore 同范式。
 */
class ActivityStore {

    private static final String STOCK_KEY_PREFIX = "act:stock:";

    private final MarketingActivityMapper activityMapper;
    private final ActivityRecordMapper recordMapper;
    private final ActivityReconciliationMapper reconMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    ActivityStore(MarketingActivityMapper activityMapper, ActivityRecordMapper recordMapper,
                  ActivityReconciliationMapper reconMapper, UserMapper userMapper,
                  RedisTemplate<String, Object> redisTemplate) {
        this.activityMapper = activityMapper;
        this.recordMapper = recordMapper;
        this.reconMapper = reconMapper;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
    }

    // ===== 活动 =====

    MarketingActivity selectActivityById(Long id) {
        return activityMapper.selectById(id);
    }

    List<MarketingActivity> selectOngoingActivities(String kind) {
        LambdaQueryWrapper<MarketingActivity> w = new LambdaQueryWrapper<MarketingActivity>()
                .eq(MarketingActivity::getStatus, "ONGOING")
                .orderByDesc(MarketingActivity::getCreatedAt);
        if (kind != null && !kind.isBlank()) {
            w.eq(MarketingActivity::getKind, kind);
        }
        return activityMapper.selectList(w);
    }

    List<MarketingActivity> selectAll() {
        return activityMapper.selectList(new LambdaQueryWrapper<MarketingActivity>()
                .orderByDesc(MarketingActivity::getCreatedAt));
    }

    /** 库存原子扣减（防超卖网关）：stock >= qty 才扣减，并返回受影响行数（0 即库存不足）。 */
    int decrementStock(Long activityId, int qty) {
        return activityMapper.update(null, new LambdaUpdateWrapper<MarketingActivity>()
                .eq(MarketingActivity::getId, activityId)
                .ge(MarketingActivity::getStock, qty)
                .setSql("stock = stock - " + qty + ", sold_count = sold_count + " + qty));
    }

    /** 库存回滚（并发重放补偿）：stock + qty，sold_count 不小于 0。 */
    int rollbackStock(Long activityId, int qty) {
        return activityMapper.update(null, new LambdaUpdateWrapper<MarketingActivity>()
                .eq(MarketingActivity::getId, activityId)
                .setSql("stock = stock + " + qty + ", sold_count = GREATEST(0, sold_count - " + qty + ")"));
    }

    /** 补贴预算累加（无限额）：subsidy_used_fen + subsidyFen。 */
    int addSubsidyUnbounded(Long id, long subsidyFen) {
        return activityMapper.update(null, new LambdaUpdateWrapper<MarketingActivity>()
                .eq(MarketingActivity::getId, id)
                .setSql("subsidy_used_fen = subsidy_used_fen + " + subsidyFen));
    }

    /** 补贴预算闸门累加：subsidy_used_fen + subsidyFen <= subsidy_budget_fen 才累加，返回受影响行数（0 即超额）。 */
    int addSubsidyBounded(Long id, long subsidyFen) {
        return activityMapper.update(null, new LambdaUpdateWrapper<MarketingActivity>()
                .eq(MarketingActivity::getId, id)
                .apply("subsidy_used_fen + " + subsidyFen + " <= subsidy_budget_fen")
                .setSql("subsidy_used_fen = subsidy_used_fen + " + subsidyFen));
    }

    /** 活动结束标记（ENDED），库存不再变更。 */
    int markEnded(Long activityId) {
        return activityMapper.update(null, Wrappers.<MarketingActivity>lambdaUpdate()
                .eq(MarketingActivity::getId, activityId)
                .set(MarketingActivity::getStatus, "ENDED"));
    }

    /** 发布态更新（DRAFT → ONGOING 等），经实体主键更新。 */
    int updateActivityStatus(Long id, String status) {
        MarketingActivity upd = new MarketingActivity();
        upd.setId(id);
        upd.setStatus(status);
        return activityMapper.updateById(upd);
    }

    // ===== 参与记录 =====

    int countUserJoined(Long activityId, Long userId) {
        Long c = recordMapper.selectCount(new LambdaQueryWrapper<ActivityRecord>()
                .eq(ActivityRecord::getActivityId, activityId)
                .eq(ActivityRecord::getUserId, userId)
                .ne(ActivityRecord::getStatus, "CANCELLED"));
        return c == null ? 0 : c.intValue();
    }

    ActivityRecord selectByIdempotencyKey(Long activityId, String idempotencyKey) {
        return recordMapper.selectOne(new LambdaQueryWrapper<ActivityRecord>()
                .eq(ActivityRecord::getActivityId, activityId)
                .eq(ActivityRecord::getIdempotencyKey, idempotencyKey));
    }

    int insertRecord(ActivityRecord rec) {
        return recordMapper.insert(rec);
    }

    List<ActivityRecord> selectByActivityId(Long activityId) {
        return recordMapper.selectList(new LambdaQueryWrapper<ActivityRecord>()
                .eq(ActivityRecord::getActivityId, activityId));
    }

    List<ActivityRecord> selectByUser(Long userId) {
        return recordMapper.selectList(new LambdaQueryWrapper<ActivityRecord>()
                .eq(ActivityRecord::getUserId, userId)
                .orderByDesc(ActivityRecord::getCreatedAt));
    }

    long countByUserSince(LocalDateTime since) {
        Long c = recordMapper.selectCount(new LambdaQueryWrapper<ActivityRecord>()
                .ge(ActivityRecord::getCreatedAt, since));
        return c == null ? 0 : c;
    }

    // ===== 对账 =====

    ActivityReconciliation selectReconByActivityId(Long activityId) {
        return reconMapper.selectOne(new LambdaQueryWrapper<ActivityReconciliation>()
                .eq(ActivityReconciliation::getActivityId, activityId));
    }

    int insertRecon(ActivityReconciliation recon) {
        return reconMapper.insert(recon);
    }

    int updateReconById(ActivityReconciliation recon) {
        return reconMapper.updateById(recon);
    }

    // ===== 用户 / 缓存 =====

    User selectUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    /** 预热库存至缓存（best-effort）：活动或库存为空则跳过；缓存异常降级依赖 DB 强一致。 */
    void preheatCache(Long activityId) {
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
}
