package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.trade.dto.ActivityCreateDTO;
import com.idlefish.trade.trade.dto.ActivityJoinDTO;
import com.idlefish.trade.trade.entity.Activity;
import com.idlefish.trade.trade.entity.ActivityGroup;
import com.idlefish.trade.trade.entity.ActivityParticipant;
import com.idlefish.trade.trade.mapper.ActivityGroupMapper;
import com.idlefish.trade.trade.mapper.ActivityMapper;
import com.idlefish.trade.trade.mapper.ActivityParticipantMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 促销活动服务（F-13.3 拼团/限时秒杀）。
 * 核心并发安全：活动库存经 {@code UPDATE ... SET stock = stock - n WHERE stock >= n} 原子扣减（affected 行网关），杜绝超卖；
 * 每人参与经唯一索引 (activity_id, user_id) 兜底。
 * 下单锁定（beginOrder）在订单创建时扣减库存并关联参与记录；关单/取消（releaseOnClose）回退。
 */
@Service
public class ActivityService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ActivityService.class);

    private final ActivityMapper activityMapper;
    private final ActivityGroupMapper groupMapper;
    private final ActivityParticipantMapper participantMapper;

    public ActivityService(ActivityMapper activityMapper, ActivityGroupMapper groupMapper,
                           ActivityParticipantMapper participantMapper) {
        this.activityMapper = activityMapper;
        this.groupMapper = groupMapper;
        this.participantMapper = participantMapper;
    }

    /** 创建活动（运营/管理端）。MVP：创建即生效；可扩展为 PENDING 待审核。 */
    @Transactional
    public Activity create(ActivityCreateDTO dto) {
        Activity a = new Activity();
        a.setItemId(dto.getItemId());
        a.setType(dto.getType());
        a.setActivityPrice(dto.getActivityPrice());
        a.setStock(dto.getStock());
        a.setSoldCount(0);
        a.setLimitPerUser(dto.getLimitPerUser() == null ? 1 : dto.getLimitPerUser());
        a.setGroupSize(dto.getGroupSize());
        a.setGroupValidMinutes(dto.getGroupValidMinutes());
        a.setStatus("ONGOING");
        a.setStartAt(dto.getStartAt());
        a.setEndAt(dto.getEndAt());
        activityMapper.insert(a);
        return a;
    }

    /** 进行中活动列表（发现页）。 */
    public List<Activity> listOngoing() {
        return activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, "ONGOING")
                .orderByDesc(Activity::getCreatedAt));
    }

    /** 活动详情。 */
    public Activity detail(Long id) {
        Activity a = activityMapper.selectById(id);
        if (a == null) {
            throw new BizException(Code.ACTIVITY_NOT_FOUND);
        }
        return a;
    }

    /**
     * 参与活动（F-13.3 拼团需先 join 占位；秒杀也可先 join）。
     * 校验活动有效性/库存/每人上限，创建或加入拼团，落参与记录（唯一索引兜底并发）。
     * 注意：此处仅占位，真正锁定活动库存发生在下单 beginOrder。
     */
    @Transactional
    public ActivityParticipant join(Long userId, ActivityJoinDTO dto) {
        Activity a = detail(dto.getActivityId());
        LocalDateTime now = LocalDateTime.now();
        ActivityValidator.assertOngoing(a, now);
        if (!a.getItemId().equals(dto.getItemId())) {
            throw new BizException(Code.BIZ_ERROR, "活动商品不匹配");
        }
        int qty = dto.getQty() == null || dto.getQty() < 1 ? 1 : dto.getQty();
        ActivityValidator.assertStock(a, qty);

        long joined = participantMapper.selectCount(new LambdaQueryWrapper<ActivityParticipant>()
                .eq(ActivityParticipant::getActivityId, a.getId())
                .eq(ActivityParticipant::getUserId, userId)
                .ne(ActivityParticipant::getStatus, "CANCELLED"));
        ActivityValidator.assertUserLimit((int) joined, a.getLimitPerUser());

        Long groupId = null;
        if ("GROUP".equals(a.getType())) {
            if (dto.getGroupNo() != null && !dto.getGroupNo().isBlank()) {
                ActivityGroup g = groupMapper.selectById(Long.parseLong(dto.getGroupNo()));
                if (g == null || !g.getActivityId().equals(a.getId()) || !"OPEN".equals(g.getStatus())) {
                    throw new BizException(Code.GROUP_NOT_FOUND, "拼团不存在或已结束");
                }
                if (g.getCurrentSize() >= g.getTargetSize()) {
                    throw new BizException(Code.GROUP_FULL, "该拼团已满");
                }
                g.setCurrentSize(g.getCurrentSize() + 1);
                if (g.getCurrentSize() >= g.getTargetSize()) {
                    g.setStatus("SUCCESS");
                }
                groupMapper.updateById(g);
                groupId = g.getId();
            } else {
                ActivityGroup g = new ActivityGroup();
                g.setActivityId(a.getId());
                g.setLeaderId(userId);
                g.setCurrentSize(1);
                g.setTargetSize(a.getGroupSize() == null ? 0 : a.getGroupSize());
                g.setStatus("OPEN");
                g.setExpireAt(now.plusMinutes(a.getGroupValidMinutes() == null ? 0 : a.getGroupValidMinutes()));
                groupMapper.insert(g);
                groupId = g.getId();
            }
        }

        ActivityParticipant p = new ActivityParticipant();
        p.setActivityId(a.getId());
        p.setUserId(userId);
        p.setGroupId(groupId);
        p.setQty(qty);
        p.setStatus("JOINED");
        try {
            participantMapper.insert(p);
        } catch (DuplicateKeyException e) {
            throw new BizException(Code.ACTIVITY_JOIN_LIMIT, "您已参与该活动");
        }
        return p;
    }

    /**
     * 下单时锁定活动库存（原子扣减防超卖）并关联订单。
     * 秒杀自包含（未 join 则在此创建参与记录）；拼团要求已 join（否则提示先参团）。
     * 返回活动价（分），供下单覆盖原价。
     */
    @Transactional
    public Long beginOrder(Long activityId, Long itemId, Long userId, int qty, String groupNo, String orderNo) {
        Activity a = detail(activityId);
        LocalDateTime now = LocalDateTime.now();
        ActivityValidator.assertOngoing(a, now);
        if (!a.getItemId().equals(itemId)) {
            throw new BizException(Code.BIZ_ERROR, "活动商品不匹配");
        }

        ActivityParticipant p;
        if ("GROUP".equals(a.getType())) {
            LambdaQueryWrapper<ActivityParticipant> w = new LambdaQueryWrapper<ActivityParticipant>()
                    .eq(ActivityParticipant::getActivityId, activityId)
                    .eq(ActivityParticipant::getUserId, userId)
                    .eq(ActivityParticipant::getStatus, "JOINED");
            if (groupNo != null && !groupNo.isBlank()) {
                w.eq(ActivityParticipant::getGroupId, Long.parseLong(groupNo));
            }
            p = participantMapper.selectOne(w);
            if (p == null) {
                throw new BizException(Code.BIZ_ERROR, "请先参与拼团");
            }
        } else {
            // 秒杀：自包含创建参与记录
            p = participantMapper.selectOne(new LambdaQueryWrapper<ActivityParticipant>()
                    .eq(ActivityParticipant::getActivityId, activityId)
                    .eq(ActivityParticipant::getUserId, userId)
                    .ne(ActivityParticipant::getStatus, "CANCELLED"));
            if (p == null) {
                ActivityValidator.assertUserLimit(0, a.getLimitPerUser());
                p = new ActivityParticipant();
                p.setActivityId(activityId);
                p.setUserId(userId);
                p.setQty(qty);
                p.setStatus("JOINED");
                try {
                    participantMapper.insert(p);
                } catch (DuplicateKeyException e) {
                    throw new BizException(Code.ACTIVITY_JOIN_LIMIT, "您已参与该活动");
                }
            }
        }

        // 原子扣减活动库存（防超卖网关）
        int rows = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .ge(Activity::getStock, qty)
                .setSql("stock = stock - " + qty + ", sold_count = sold_count + " + qty));
        if (rows == 0) {
            throw new BizException(Code.STOCK_NOT_ENOUGH, "活动库存不足");
        }

        p.setOrderNo(orderNo);
        p.setQty(qty);
        p.setStatus("PAID_PENDING");
        participantMapper.updateById(p);
        return a.getActivityPrice();
    }

    /** 关单/取消时释放活动库存并回退参与记录与拼团人数（幂等）。 */
    @Transactional
    public void releaseOnClose(Long activityId, String orderNo) {
        if (activityId == null || orderNo == null) {
            return;
        }
        ActivityParticipant p = participantMapper.selectOne(new LambdaQueryWrapper<ActivityParticipant>()
                .eq(ActivityParticipant::getActivityId, activityId)
                .eq(ActivityParticipant::getOrderNo, orderNo));
        if (p == null || !"PAID_PENDING".equals(p.getStatus())) {
            return;
        }
        // 归还活动库存
        activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, activityId)
                .setSql("stock = stock + " + p.getQty() + ", sold_count = GREATEST(0, sold_count - " + p.getQty() + ")"));
        // 回退参与记录
        p.setStatus("CANCELLED");
        participantMapper.updateById(p);
        // 拼团人数回退（仅 OPEN 团，已 SUCCESS 的团保持成团）
        if (p.getGroupId() != null) {
            groupMapper.update(null, new LambdaUpdateWrapper<ActivityGroup>()
                    .eq(ActivityGroup::getId, p.getGroupId())
                    .eq(ActivityGroup::getStatus, "OPEN")
                    .setSql("current_size = GREATEST(1, current_size - 1)"));
        }
    }

    /** 定时：活动到期自动结束 + 拼团超时未成团自动失败并释放锁定库存。 */
    @Scheduled(fixedDelay = 60_000)
    public void closeExpired() {
        LocalDateTime now = LocalDateTime.now();
        try {
            activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                    .eq(Activity::getStatus, "ONGOING")
                    .isNotNull(Activity::getEndAt)
                    .le(Activity::getEndAt, now)
                    .set(Activity::getStatus, "ENDED"));
        } catch (Exception ignored) {
            // 调度容错
        }
        try {
            List<ActivityGroup> expired = groupMapper.selectList(new LambdaQueryWrapper<ActivityGroup>()
                    .eq(ActivityGroup::getStatus, "OPEN")
                    .isNotNull(ActivityGroup::getExpireAt)
                    .le(ActivityGroup::getExpireAt, now));
            for (ActivityGroup g : expired) {
                g.setStatus("FAILED");
                groupMapper.updateById(g);
                List<ActivityParticipant> members = participantMapper.selectList(new LambdaQueryWrapper<ActivityParticipant>()
                        .eq(ActivityParticipant::getGroupId, g.getId())
                        .in(ActivityParticipant::getStatus, "JOINED", "PAID_PENDING"));
                int locked = members.stream().mapToInt(ActivityParticipant::getQty).sum();
                if (locked > 0) {
                    activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                            .eq(Activity::getId, g.getActivityId())
                            .setSql("stock = stock + " + locked + ", sold_count = GREATEST(0, sold_count - " + locked + ")"));
                }
                for (ActivityParticipant m : members) {
                    if ("PAID_PENDING".equals(m.getStatus())) {
                        log.warn("[activity] 拼团失败且已支付，需人工退款：activity={}, group={}, user={}, order={}",
                                g.getActivityId(), g.getId(), m.getUserId(), m.getOrderNo());
                    }
                    m.setStatus("CANCELLED");
                    participantMapper.updateById(m);
                }
            }
        } catch (Exception ignored) {
            // 调度容错
        }
    }

    /** 我的活动参与记录（按时间倒序）。 */
    public List<ActivityParticipant> myParticipants(Long userId) {
        return participantMapper.selectList(new LambdaQueryWrapper<ActivityParticipant>()
                .eq(ActivityParticipant::getUserId, userId).orderByDesc(ActivityParticipant::getCreatedAt));
    }
}
