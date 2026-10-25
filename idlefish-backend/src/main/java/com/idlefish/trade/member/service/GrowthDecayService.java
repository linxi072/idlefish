package com.idlefish.trade.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.member.GrowthValueCalculator;
import com.idlefish.trade.member.entity.UserGrowth;
import com.idlefish.trade.member.mapper.UserGrowthMapper;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 成长值不活跃衰减服务（ITER-F07 / REQ-05）：定时扫描 → 按规则衰减 → 低于阈值降级 → 提前 7 日通知。
 * <p>
 * <b>扫描策略</b>：游标分批（{@code id > lastId order by id asc limit 500}）避免深分页；
 * SQL 侧先按活跃时间粗筛（衰减：已过阈值；预警：距阈值 N 日内），Java 侧用
 * {@link GrowthValueCalculator} 纯函数精算，杜绝「差一天误判」。
 * </p>
 * <p><b>幂等</b>：衰减 bizId 取日期串（{@code 2026-10-23}），配合
 * {@code (user_id, biz_type, biz_id)} 唯一索引保证每用户每日最多衰减一次；
 * 预警用 {@code decayNotifiedAt} 当日去重。</p>
 * <p><b>冻结账户</b>：衰减对冻结账户同样生效——封禁期间不活跃不应「保级」；
 * 解冻不回补已衰减的成长值（PRD 边界场景约定）。</p>
 * <p><b>失败隔离</b>：单用户异常仅记指标，不影响整批；调度入口自身 try/catch 保证不逃逸。</p>
 */
@Service
public class GrowthDecayService {

    private static final int BATCH = 500;

    private final UserGrowthMapper userGrowthMapper;
    private final GrowthValueService growthValueService;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;
    private final IdlefishProperties.Growth growth;
    private final IdlefishProperties.Notify notify;

    public GrowthDecayService(UserGrowthMapper userGrowthMapper,
                              GrowthValueService growthValueService,
                              NotificationService notificationService,
                              MetricsRegistry metrics,
                              IdlefishProperties idlefishProperties) {
        this.userGrowthMapper = userGrowthMapper;
        this.growthValueService = growthValueService;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.growth = idlefishProperties.getGrowth();
        this.notify = idlefishProperties.getNotify();
    }

    /**
     * 衰减扫描结果。
     *
     * @param scanned  本轮扫描账户数
     * @param decayed  实际发生衰减的账户数
     * @param downgraded 衰减后等级下调的账户数
     * @param notified 发送「即将衰减」预警的账户数（仅 {@link #notifyUpcomingDecay} 使用）
     */
    public record DecaySummary(long scanned, long decayed, long downgraded, long notified) {

        public static DecaySummary empty() {
            return new DecaySummary(0, 0, 0, 0);
        }
    }

    /**
     * 扫描并执行衰减（REQ-05 主流程）：游标分批 → 粗筛（SQL）→ 精算（纯函数）→
     * 幂等衰减 → 降级重算（{@code GrowthValueService.applyDecay} 内部完成）。
     */
    public DecaySummary scanAndDecay() {
        if (!growth.isEnabled() || !growth.isDecayEnabled()
                || growth.getDecayThresholdDays() <= 0 || growth.getDecayPerDay() <= 0) {
            return DecaySummary.empty();
        }
        long scanned = 0;
        long decayed = 0;
        long downgraded = 0;
        String dateBizId = LocalDate.now().toString();
        Long lastId = null;
        while (true) {
            List<UserGrowth> batch = fetchDueBatch(lastId);
            if (batch.isEmpty()) {
                break;
            }
            for (UserGrowth g : batch) {
                scanned++;
                lastId = g.getId();
                try {
                    long inactive = growthValueService.inactiveDaysOf(g);
                    if (!GrowthValueCalculator.decayDue(inactive, growth.getDecayThresholdDays())) {
                        continue;
                    }
                    int before = g.getGrowthValue() == null ? 0 : g.getGrowthValue();
                    int after = GrowthValueCalculator.decay(before, inactive,
                            growth.getDecayThresholdDays(), growth.getDecayPerDay(), growth.getDecayFloor());
                    if (after >= before) {
                        continue;
                    }
                    String beforeLevel = g.getLevelCode();
                    boolean ok = growthValueService.applyDecay(g.getUserId(), before, after, dateBizId,
                            "不活跃衰减（" + inactive + " 天未活跃，每衰减日扣 " + growth.getDecayPerDay() + " 点）");
                    if (!ok) {
                        continue;
                    }
                    decayed++;
                    String afterLevel = levelOf(g.getUserId());
                    if (!Objects.equals(beforeLevel, afterLevel)) {
                        downgraded++;
                        metrics.increment("growth.decay.downgrade");
                    }
                } catch (Exception e) {
                    // 单用户失败不影响整批扫描
                    metrics.increment("growth.decay.scan.error");
                }
            }
            if (batch.size() < BATCH) {
                break;
            }
        }
        notifyAdmin("成长值衰减扫描完成",
                "扫描 " + scanned + " 户，衰减 " + decayed + " 户，降级 " + downgraded + " 户");
        return new DecaySummary(scanned, decayed, downgraded, 0);
    }

    /**
     * 即将衰减提前通知（REQ-05 提前 7 日）：距衰减日落在 (0, notifyDays] 且当日未通知过才发送，
     * 发送后写 {@code decayNotifiedAt} 去重。
     */
    public DecaySummary notifyUpcomingDecay() {
        if (!growth.isEnabled() || !growth.isDecayEnabled() || growth.getDecayThresholdDays() <= 0) {
            return DecaySummary.empty();
        }
        long scanned = 0;
        long notified = 0;
        Long lastId = null;
        while (true) {
            List<UserGrowth> batch = fetchApproachingBatch(lastId);
            if (batch.isEmpty()) {
                break;
            }
            for (UserGrowth g : batch) {
                scanned++;
                lastId = g.getId();
                try {
                    long inactive = growthValueService.inactiveDaysOf(g);
                    boolean notifiedToday = g.getDecayNotifiedAt() != null
                            && g.getDecayNotifiedAt().toLocalDate().equals(LocalDate.now());
                    if (!GrowthValueCalculator.shouldNotifyDecay(inactive, growth.getDecayThresholdDays(),
                            growth.getDecayNotifyDays(), notifiedToday)) {
                        continue;
                    }
                    int left = GrowthValueCalculator.daysUntilDecay(inactive, growth.getDecayThresholdDays());
                    notify(g.getUserId(), NotificationType.GROWTH_DECAY_WARNING, "成长值即将衰减",
                            "您已 " + inactive + " 天未活跃，还有 " + left + " 天将开始衰减成长值"
                                    + "（每衰减日扣 " + growth.getDecayPerDay() + " 点），完成交易或发布商品即可保持活跃");
                    if (growthValueService.markDecayNotified(g.getUserId())) {
                        notified++;
                    }
                    metrics.increment("growth.decay.notify");
                } catch (Exception e) {
                    metrics.increment("growth.decay.notify.error");
                }
            }
            if (batch.size() < BATCH) {
                break;
            }
        }
        notifyAdmin("成长值衰减预警完成", "扫描 " + scanned + " 户，发送预警 " + notified + " 户");
        return new DecaySummary(scanned, 0, 0, notified);
    }

    // ===== 内部：游标分批（避免深分页）=====

    /** 衰减批次：已过不活跃阈值的账户（lastActiveAt 为空回退 createdAt 的账户也纳入，Java 侧精算）。 */
    private List<UserGrowth> fetchDueBatch(Long afterId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, growth.getDecayThresholdDays()));
        return userGrowthMapper.selectList(new LambdaQueryWrapper<UserGrowth>()
                .gt(UserGrowth::getId, afterId == null ? 0L : afterId)
                .and(w -> w.lt(UserGrowth::getLastActiveAt, cutoff)
                        .or().isNull(UserGrowth::getLastActiveAt))
                .orderByAsc(UserGrowth::getId)
                .last("limit " + BATCH));
    }

    /** 预警批次：距衰减日 <= notifyDays 且尚未进入衰减期的账户（lastActiveAt 缺失者无基准，不预警）。 */
    private List<UserGrowth> fetchApproachingBatch(Long afterId) {
        int threshold = growth.getDecayThresholdDays();
        int notifyDays = Math.max(1, growth.getDecayNotifyDays());
        LocalDateTime from = LocalDateTime.now().minusDays(threshold);
        LocalDateTime to = LocalDateTime.now().minusDays(Math.max(0, threshold - notifyDays));
        return userGrowthMapper.selectList(new LambdaQueryWrapper<UserGrowth>()
                .gt(UserGrowth::getId, afterId == null ? 0L : afterId)
                .ge(UserGrowth::getLastActiveAt, from)
                .lt(UserGrowth::getLastActiveAt, to)
                .orderByAsc(UserGrowth::getId)
                .last("limit " + BATCH));
    }

    private String levelOf(Long userId) {
        UserGrowth g = userGrowthMapper.selectOne(new LambdaQueryWrapper<UserGrowth>()
                .eq(UserGrowth::getUserId, userId));
        return g == null ? null : g.getLevelCode();
    }

    private void notify(Long userId, NotificationType type, String title, String content) {
        try {
            notificationService.notify(userId, type, String.valueOf(userId), title, content);
        } catch (Exception ignored) {
            // 通知 best-effort
        }
    }

    /** 运营告警：衰减日报 / 预警汇总发往成长值专属管理员，未配置回退 notify.alertAdminUserId。 */
    private void notifyAdmin(String title, String content) {
        Long adminId = growth.getAlertAdminUserId() != null ? growth.getAlertAdminUserId()
                : notify.getAlertAdminUserId();
        if (adminId == null) {
            return;
        }
        notify(adminId, NotificationType.SYSTEM_ALERT, title, content);
    }
}
