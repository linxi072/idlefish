package com.idlefish.trade.marketing.activity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.marketing.activity.ActivityCore;
import com.idlefish.trade.marketing.activity.ActivityKind;
import com.idlefish.trade.marketing.activity.SeckillAsyncDispatcher;
import com.idlefish.trade.marketing.activity.SeckillSettleCommand;
import com.idlefish.trade.marketing.activity.entity.ActivityRecord;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.marketing.activity.vo.ActivityJoinResultVO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 直播秒杀玩法服务（ITER-F08 / REQ-07）。
 * <p>
 * 峰值模型：缓存预扣（{@code act:stock:{activityId}}）做高速准入与快速拒绝；准入成功后下发异步结算指令，
 * 经 {@link SeckillAsyncDispatcher}（RocketMQ 削峰 + DB 兜底）最终由 {@link #settle} 落库。
 * 落库复用统一活动内核的原子扣减 + 幂等，保证零超卖、零重复发放（REQ-03/REQ-06）。
 * 缓存不可用或库存未预热时降级为 DB 强一致（不阻塞、不超卖）。
 */
@Service
public class LiveSeckillService {

    private static final String STOCK_KEY_PREFIX = "act:stock:";

    private final ActivityKernelService kernel;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SeckillAsyncDispatcher dispatcher;
    private final MetricsRegistry metrics;
    private final ObjectMapper objectMapper;

    public LiveSeckillService(ActivityKernelService kernel, RedisTemplate<String, Object> redisTemplate,
                              SeckillAsyncDispatcher dispatcher, MetricsRegistry metrics, ObjectMapper objectMapper) {
        this.kernel = kernel;
        this.redisTemplate = redisTemplate;
        this.dispatcher = dispatcher;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /**
     * 抢购入口（REQ-07）。
     * @return 缓存预扣成功返回 {@code ACCEPTED}（异步落库）；降级路径返回 {@code SETTLED}（同步落库）。
     */
    public ActivityJoinResultVO grab(Long userId, String deviceId, Long activityId, String requestId) {
        MarketingActivity a = kernel.load(activityId);
        if (!ActivityKind.LIVE_SECKILL.name().equals(a.getKind())) {
            throw new BizException(Code.ACTIVITY_KIND_NOT_SUPPORTED);
        }
        ActivityCore.assertOngoing(a, LocalDateTime.now());
        String idempotencyKey = "seckill:" + userId + ":" + activityId + ":"
                + (requestId == null ? UUID.randomUUID().toString() : requestId);
        String key = STOCK_KEY_PREFIX + activityId;

        // 缓存预扣（REQ-07）：高速准入 + 快速拒绝
        boolean preDeducted;
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
                preDeducted = false; // 未预热 → 走 DB 强一致兜底
            } else {
                Long remain = redisTemplate.opsForValue().decrement(key);
                if (remain == null || remain < 0) {
                    if (remain != null) {
                        redisTemplate.opsForValue().increment(key);
                    }
                    metrics.increment("seckill.prededuct.exhausted");
                    throw new BizException(Code.LIVE_SECKILL_PREEMPTED);
                }
                preDeducted = true;
            }
        } catch (BizException be) {
            throw be;
        } catch (RuntimeException e) {
            // Redis 不可用 → 降级 DB 强一致（不阻塞主流程）
            preDeducted = false;
        }

        SeckillSettleCommand cmd = new SeckillSettleCommand();
        cmd.setIdempotencyKey(idempotencyKey);
        cmd.setUserId(userId);
        cmd.setActivityId(activityId);
        cmd.setDeviceId(deviceId);
        cmd.setBizId("seckill:" + activityId + ":" + userId);

        if (preDeducted) {
            dispatcher.dispatch(cmd); // 异步落库（MQ 削峰 + DB 兜底）
            metrics.increment("seckill.grab.accepted");
            ActivityJoinResultVO vo = new ActivityJoinResultVO();
            vo.setActivityId(activityId);
            vo.setKind(a.getKind());
            vo.setStatus("ACCEPTED");
            vo.setRecordNo(idempotencyKey);
            return vo;
        }
        // 降级：DB 强一致同步结算
        ActivityRecord rec = kernel.participate(userId, deviceId, activityId, idempotencyKey,
                cmd.getBizId(), 1, 0, 0, null, "SETTLED", null);
        metrics.increment("seckill.grab.settled_sync");
        return toResult(rec);
    }

    /** 异步结算落库（REQ-07）：复用内核原子扣减 + 幂等，最终一致且仅生效一次。 */
    @Transactional
    public ActivityRecord settle(SeckillSettleCommand cmd) {
        return kernel.participate(cmd.getUserId(), cmd.getDeviceId(), cmd.getActivityId(),
                cmd.getIdempotencyKey(), cmd.getBizId(), 1, 0, 0, null, "SETTLED", null);
    }

    private ActivityJoinResultVO toResult(ActivityRecord rec) {
        ActivityJoinResultVO vo = new ActivityJoinResultVO();
        vo.setRecordNo(rec.getRecordNo());
        vo.setActivityId(rec.getActivityId());
        vo.setKind(rec.getKind());
        vo.setStatus(rec.getStatus());
        vo.setAmountFen(rec.getAmountFen());
        vo.setCouponId(rec.getCouponId());
        vo.setExtra(rec.getExtra());
        return vo;
    }
}
