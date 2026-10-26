package com.idlefish.trade.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.DateTimeUtil;
import com.idlefish.trade.member.GrowthAntiFraud;
import com.idlefish.trade.member.GrowthValueCalculator;
import com.idlefish.trade.member.MemberLevelCalculator;
import com.idlefish.trade.member.entity.GrowthValueLog;
import com.idlefish.trade.member.entity.MemberLevel;
import com.idlefish.trade.member.entity.UserGrowth;
import com.idlefish.trade.member.mapper.GrowthValueLogMapper;
import com.idlefish.trade.member.mapper.MemberLevelMapper;
import com.idlefish.trade.member.mapper.UserGrowthMapper;
import com.idlefish.trade.user.mapper.UserMapper;
import com.idlefish.trade.member.vo.GrowthAccountAdminVO;
import com.idlefish.trade.member.vo.GrowthAccountVO;
import com.idlefish.trade.member.vo.GrowthValueLogVO;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.risk.entity.DeviceFingerprint;
import com.idlefish.trade.risk.mapper.DeviceFingerprintMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 成长值服务（ITER-F07 / PRD-P2-F07）：多维累计、刷分冻结、判责扣减、退款冲正与全量流水。
 * <p>
 * <b>与 F-13.2 {@link MemberLevelService} 的关系</b>：后者负责等级配置 CRUD 与用户端等级视图；
 * 本服务负责成长值的「写入侧」（累计 / 扣减 / 冲正 / 冻结 / 流水），等级解析复用
 * {@link MemberLevelCalculator} 与 {@code t_member_level} 配置，避免两套等级口径。
 * </p>
 * <p><b>可靠性设计</b>：</p>
 * <ul>
 *   <li>发放幂等：{@code (user_id, biz_type, biz_id)} 唯一索引兜底，重复订单 / 重复评价不重复计数；</li>
 *   <li>并发安全：成长值更新带 CAS（{@code growth_value = 期望值}），失败有限重试，杜绝丢失更新；</li>
 *   <li>fail-closed：账户冻结 / 防刷命中一律不发，且写入 0 增量流水留痕（REQ-09）；</li>
 *   <li>best-effort：所有入口均不抛业务异常阻断交易 / 评价 / 验货 / 维权主流程，异常仅记日志与指标。</li>
 * </ul>
 * <p>纯计算见 {@link GrowthValueCalculator} 与 {@link GrowthAntiFraud}（无副作用，离线单测覆盖）。</p>
 */
@Service
public class GrowthValueService {

    // ===== 来源常量（持久化到 t_growth_value_log.biz_type）=====
    /** 交易成功获得。 */
    public static final String BIZ_EARN_TRADE = "EARN_TRADE";
    /** 发布商品获得。 */
    public static final String BIZ_EARN_PUBLISH = "EARN_PUBLISH";
    /** 评价通过获得。 */
    public static final String BIZ_EARN_REVIEW = "EARN_REVIEW";
    /** 验货配合（F-02 验货单完成）获得。 */
    public static final String BIZ_EARN_INSPECTION = "EARN_INSPECTION";
    /** 纠纷和解（F-17 判责且双方和解）获得。 */
    public static final String BIZ_EARN_RECONCILE = "EARN_RECONCILE";
    /** F-17 判责扣减（REQ-10，申诉成功可回补）。 */
    public static final String BIZ_DEDUCT_DISPUTE = "DEDUCT_DISPUTE";
    /** 退款 / 退货冲正（负向流水）。 */
    public static final String BIZ_DEDUCT_REVERSE = "DEDUCT_REVERSE";
    /** 不活跃衰减（REQ-05）。 */
    public static final String BIZ_DECAY = "DECAY";
    /** 申诉成功回补判责扣减（REQ-10）。 */
    public static final String BIZ_RESTORE_DISPUTE = "RESTORE_DISPUTE";

    private static final int MAX_RETRY = 3;
    private static final String DEFAULT_LEVEL = "L1";

    private final UserGrowthMapper userGrowthMapper;
    private final UserMapper userMapper;
    private final GrowthValueLogMapper growthValueLogMapper;
    private final MemberLevelMapper memberLevelMapper;
    private final DeviceFingerprintMapper deviceFingerprintMapper;
    private final NotificationService notificationService;
    private final MetricsRegistry metrics;
    private final IdlefishProperties.Growth growth;

    public GrowthValueService(UserGrowthMapper userGrowthMapper,
                              UserMapper userMapper,
                              GrowthValueLogMapper growthValueLogMapper,
                              MemberLevelMapper memberLevelMapper,
                              DeviceFingerprintMapper deviceFingerprintMapper,
                              NotificationService notificationService,
                              MetricsRegistry metrics,
                              IdlefishProperties idlefishProperties) {
        this.userGrowthMapper = userGrowthMapper;
        this.userMapper = userMapper;
        this.growthValueLogMapper = growthValueLogMapper;
        this.memberLevelMapper = memberLevelMapper;
        this.deviceFingerprintMapper = deviceFingerprintMapper;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.growth = idlefishProperties.getGrowth();
    }

    // ===== 对外：多维累计（REQ-02）=====

    /** 交易成功：按实付金额累计成长值（PayService.onOrderPaid 调用）。 */
    public int earnTrade(Long userId, String orderNo, long payAmountFen) {
        if (!growth.isEnabled() || userId == null || orderNo == null || orderNo.isBlank()) {
            return 0;
        }
        int delta = GrowthValueCalculator.fromTrade(payAmountFen, growth.getTradeGrowthPerYuanFen(),
                growth.getTradeCapPerOrder());
        return earn(userId, BIZ_EARN_TRADE, orderNo, delta, payAmountFen, "交易完成得成长值");
    }

    /** 发布商品：固定成长值（ItemService.publish 调用）。 */
    public int earnPublish(Long userId, Long itemId) {
        if (!growth.isEnabled() || userId == null || itemId == null) {
            return 0;
        }
        return earn(userId, BIZ_EARN_PUBLISH, String.valueOf(itemId),
                GrowthValueCalculator.fromPublish(growth.getPublishFixed()), 0L, "发布商品得成长值");
    }

    /** 评价通过：固定成长值（ReviewService.approve 调用）。 */
    public int earnReview(Long userId, Long reviewId) {
        if (!growth.isEnabled() || userId == null || reviewId == null) {
            return 0;
        }
        return earn(userId, BIZ_EARN_REVIEW, String.valueOf(reviewId),
                GrowthValueCalculator.fromReview(growth.getReviewFixed()), 0L, "评价通过得成长值");
    }

    /** 验货配合：固定成长值（InspectionService 完成时调用）。 */
    public int earnInspection(Long userId, String inspectionNo) {
        if (!growth.isEnabled() || userId == null || inspectionNo == null || inspectionNo.isBlank()) {
            return 0;
        }
        return earn(userId, BIZ_EARN_INSPECTION, inspectionNo,
                GrowthValueCalculator.fromInspection(growth.getInspectionFixed()), 0L, "验货配合得成长值");
    }

    /** 纠纷和解：固定成长值（DisputeService 和解/裁决履行后调用）。 */
    public int earnReconcile(Long userId, String disputeNo) {
        if (!growth.isEnabled() || userId == null || disputeNo == null || disputeNo.isBlank()) {
            return 0;
        }
        return earn(userId, BIZ_EARN_RECONCILE, disputeNo,
                GrowthValueCalculator.fromReconcile(growth.getReconcileFixed()), 0L, "纠纷和解得成长值");
    }

    // ===== 对外：扣减 / 冲正 / 回补 =====

    /**
     * F-17 判责扣减成长值（默认量，REQ-10）：扣减量取配置 {@code growth.disputePenaltyCap}，
     * 按工单号幂等。供维权裁决等「按规则判责」场景调用，无需调用方感知配置。
     */
    @Transactional
    public int deductByDispute(Long userId, String disputeNo, String remark) {
        return deductByDispute(userId, disputeNo, growth.getDisputePenaltyCap(), remark);
    }

    /**
     * F-17 判责扣减成长值（REQ-10）：按工单号幂等；申诉成功通过 {@link #restoreByDispute} 回补。
     * 扣减不受「发放通道冻结」限制（冻结只拦截获得，不豁免判责）。
     */
    @Transactional
    public int deductByDispute(Long userId, String disputeNo, int amount, String remark) {
        if (!growth.isEnabled() || userId == null || disputeNo == null || disputeNo.isBlank() || amount <= 0) {
            return 0;
        }
        if (alreadyLogged(userId, BIZ_DEDUCT_DISPUTE, disputeNo)) {
            return 0;
        }
        UserGrowth g = ensure(userId);
        int before = valueOf(g);
        int after = GrowthValueCalculator.penalty(before, amount, growth.getDisputePenaltyCap());
        int actual = before - after;
        if (actual <= 0) {
            return 0;
        }
        if (!casUpdate(g.getId(), before, after, 0)) {
            return 0;
        }
        writeLog(userId, BIZ_DEDUCT_DISPUTE, disputeNo, -actual, before, after, g.getLevelCode(),
                "维权判责扣减成长值：" + safe(remark));
        applyLevel(userId, after);
        metrics.increment("growth.deduct.dispute");
        notify(userId, NotificationType.SYSTEM_ALERT, "成长值扣减",
                "因维权判责扣减 " + actual + " 点成长值");
        return actual;
    }

    /** 申诉成功回补（REQ-10）：仅回补此前判责扣减的量，按工单号幂等。 */
    @Transactional
    public int restoreByDispute(Long userId, String disputeNo, int amount, String remark) {
        if (!growth.isEnabled() || userId == null || disputeNo == null || disputeNo.isBlank() || amount <= 0) {
            return 0;
        }
        if (alreadyLogged(userId, BIZ_RESTORE_DISPUTE, disputeNo)) {
            return 0;
        }
        return earn(userId, BIZ_RESTORE_DISPUTE, disputeNo, amount, 0L,
                "申诉成功回补成长值：" + safe(remark), false);
    }

    /**
     * 申诉成功回补（REQ-10，便捷重载）：自动反查该工单的历史判责扣减量并回补，按工单号幂等。
     * 供维权申诉成功流调用，调用方无需感知扣减幅度；无判责扣减（或已回补）时返回 0。
     */
    @Transactional
    public int restoreByDispute(Long userId, String disputeNo) {
        if (!growth.isEnabled() || userId == null || disputeNo == null || disputeNo.isBlank()) {
            return 0;
        }
        if (alreadyLogged(userId, BIZ_RESTORE_DISPUTE, disputeNo)) {
            return 0;
        }
        long deducted = growthValueLogMapper.sumAbsDeltaByBiz(userId, BIZ_DEDUCT_DISPUTE, disputeNo);
        if (deducted <= 0) {
            return 0; // 无判责扣减可回补
        }
        int amount = (int) Math.min(deducted, Integer.MAX_VALUE);
        return restoreByDispute(userId, disputeNo, amount, "申诉成功自动回补");
    }

    /**
     * 退款 / 退货冲正：按订单号找到该订单的交易成长值流水，逐用户扣回（负向流水）。
     * 与 {@code PointService.releaseByOrder} 同范式，幂等可重复调用。
     */
    @Transactional
    public void reverseByOrder(String orderNo) {
        if (!growth.isEnabled() || orderNo == null || orderNo.isBlank()) {
            return;
        }
        List<GrowthValueLog> earned = growthValueLogMapper.selectList(new LambdaQueryWrapper<GrowthValueLog>()
                .eq(GrowthValueLog::getBizType, BIZ_EARN_TRADE)
                .eq(GrowthValueLog::getBizId, orderNo));
        for (GrowthValueLog lg : earned) {
            if (lg.getDelta() == null || lg.getDelta() <= 0) {
                continue;
            }
            if (alreadyLogged(lg.getUserId(), BIZ_DEDUCT_REVERSE, orderNo)) {
                continue;
            }
            deductInternal(lg.getUserId(), BIZ_DEDUCT_REVERSE, orderNo, lg.getDelta(), "退款冲正成长值");
        }
    }

    // ===== 对外：账户 / 流水 / 冻结 / 活跃 =====

    /** 查询或惰性创建成长值账户。 */
    public UserGrowth account(Long userId) {
        if (userId == null) {
            return null;
        }
        return ensure(userId);
    }

    /** 成长值流水（分页，倒序）——REQ-09 可对账。 */
    public IPage<GrowthValueLogVO> logs(Long userId, int page, int size) {
        return logs(userId, null, page, size);
    }

    /**
     * 成长值流水（分页，倒序，可按来源过滤）——REQ-09 可对账；{@code bizType} 为空时查全部。
     */
    public IPage<GrowthValueLogVO> logs(Long userId, String bizType, int page, int size) {
        Page<GrowthValueLog> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<GrowthValueLog> q = new LambdaQueryWrapper<GrowthValueLog>()
                .eq(GrowthValueLog::getUserId, userId)
                .orderByDesc(GrowthValueLog::getCreatedAt);
        if (bizType != null && !bizType.isBlank()) {
            q.eq(GrowthValueLog::getBizType, bizType);
        }
        IPage<GrowthValueLog> r = growthValueLogMapper.selectPage(p, q);
        return r.convert(this::toVO);
    }

    /**
     * 用户端成长中心账户视图（REQ-11）：等级权益 + 升级进度 + 冻结状态 + 衰减预警。
     * 与 F-13.2 {@code MemberLevelService.getLevel} 的区别：叠加累计获得 / 冻结 / 距衰减天数，
     * 是「我的成长中心」唯一数据源；等级解析口径与 F-13.2 完全一致（同一张 t_member_level）。
     */
    public GrowthAccountVO accountView(Long userId) {
        if (userId == null) {
            return null;
        }
        UserGrowth g = ensure(userId);
        return toAccountVO(g, true);
    }

    /**
     * 后台账户视图（只读）：不惰性创建账户，避免后台查询对 DB 产生写入副作用；
     * 账户不存在时返回全 null 视图（由控制器按「无账户」处理）。
     */
    public GrowthAccountVO accountViewReadonly(Long userId) {
        if (userId == null) {
            return null;
        }
        UserGrowth g = userGrowthMapper.selectOne(new LambdaQueryWrapper<UserGrowth>()
                .eq(UserGrowth::getUserId, userId));
        if (g == null) {
            GrowthAccountVO empty = new GrowthAccountVO();
            empty.setGrowthValue(0);
            empty.setTotalEarned(0);
            empty.setFrozen(false);
            empty.setDaysUntilDecay(0);
            empty.setRuleVersion(growth.getRuleVersion());
            return empty;
        }
        return toAccountVO(g, false);
    }

    /**
     * 存量用户初始化（PRD §12.4 遗留项②）：为尚未建立成长值账户的存量用户批量创建
     * 默认 L1 / 0 成长值账户。幂等 best-effort——复用 {@link #ensure} 的惰性创建逻辑，
     * 唯一索引兜底不会产生重复账户；单条失败不影响其余。
     *
     * @param limit 单次最多初始化用户数（防止超大事务，后台分页调用）
     * @return 实际初始化的账户数量
     */
    @Transactional
    public int initMissingUsers(int limit) {
        if (!growth.isEnabled() || limit <= 0) {
            return 0;
        }
        List<Long> ids = userMapper.selectMissingGrowthUserIds(Math.max(1, limit));
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long userId : ids) {
            try {
                UserGrowth created = ensure(userId);
                if (created != null) {
                    count++;
                }
            } catch (Exception e) {
                // best-effort：单条初始化失败仅记指标，不阻断其余存量用户
                metrics.increment("growth.init.error");
            }
        }
        if (count > 0) {
            metrics.increment("growth.init.stock");
        }
        return count;
    }

    /**
     * 运营后台成长值账户列表（REQ-11 后台可见）：可按冻结状态过滤，
     * 派生等级名称与距衰减天数，支撑「刷分名单冻结」与「流失预警」两个运营场景。
     */
    public IPage<GrowthAccountAdminVO> listAccounts(int page, int size, Boolean frozenOnly) {
        Page<UserGrowth> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<UserGrowth> q = new LambdaQueryWrapper<>();
        if (frozenOnly != null) {
            q.eq(UserGrowth::getGrowthFrozen, frozenOnly ? 1 : 0);
        }
        q.orderByDesc(UserGrowth::getUpdatedAt);
        IPage<UserGrowth> r = userGrowthMapper.selectPage(p, q);
        List<MemberLevel> tiers = tiers();
        List<GrowthAccountAdminVO> list = r.getRecords().stream()
                .map(g -> toAdminVO(g, tiers))
                .toList();
        IPage<GrowthAccountAdminVO> out = new Page<>(r.getCurrent(), r.getSize(), r.getTotal());
        out.setRecords(list);
        return out;
    }

    /**
     * 按最新成长值重算等级（公开变体，供衰减降级等批处理复用 REQ-04 的升级 / 降级口径）。
     */
    public void recomputeLevel(Long userId) {
        if (userId == null) {
            return;
        }
        UserGrowth g = userGrowthMapper.selectOne(new LambdaQueryWrapper<UserGrowth>()
                .eq(UserGrowth::getUserId, userId));
        if (g == null) {
            return;
        }
        applyLevel(userId, valueOf(g));
    }

    /** 账户距最后活跃的天数（REQ-05 时间基准）：lastActiveAt 为空回退 createdAt。 */
    public long inactiveDaysOf(UserGrowth g) {
        if (g == null) {
            return 0;
        }
        LocalDateTime last = g.getLastActiveAt() != null ? g.getLastActiveAt() : g.getCreatedAt();
        if (last == null) {
            return 0;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(last, LocalDateTime.now());
        return Math.max(0, days);
    }

    /** 冻结发放通道（REQ-03）：命中刷分或账号封禁时调用；解冻不回补已跳过的成长值。 */
    @Transactional
    public boolean freeze(Long userId, String reason) {
        if (userId == null) {
            return false;
        }
        UserGrowth g = ensure(userId);
        if (g.isFrozen()) {
            return false;
        }
        int rows = userGrowthMapper.update(null, new LambdaUpdateWrapper<UserGrowth>()
                .eq(UserGrowth::getId, g.getId())
                .set(UserGrowth::getGrowthFrozen, 1));
        if (rows > 0) {
            metrics.increment("growth.freeze");
            notify(userId, NotificationType.SYSTEM_ALERT, "成长值通道已冻结",
                    "因" + safe(reason) + "，成长值发放通道已冻结");
        }
        return rows > 0;
    }

    /** 解冻发放通道；已跳过的成长值不回补（PRD 边界场景约定）。 */
    @Transactional
    public boolean unfreeze(Long userId) {
        if (userId == null) {
            return false;
        }
        UserGrowth g = ensure(userId);
        if (!g.isFrozen()) {
            return false;
        }
        return userGrowthMapper.update(null, new LambdaUpdateWrapper<UserGrowth>()
                .eq(UserGrowth::getId, g.getId())
                .set(UserGrowth::getGrowthFrozen, 0)) > 0;
    }

    /** 刷新活跃时间（REQ-05 衰减基准）；用户端任意正向行为均可调用。 */
    @Transactional
    public boolean touchActive(Long userId) {
        if (userId == null) {
            return false;
        }
        UserGrowth g = ensure(userId);
        return userGrowthMapper.update(null, new LambdaUpdateWrapper<UserGrowth>()
                .eq(UserGrowth::getId, g.getId())
                .set(UserGrowth::getLastActiveAt, LocalDateTime.now())) > 0;
    }

    /**
     * 应用不活跃衰减（REQ-05，由 {@code GrowthDecayService} 调度调用）：
     * <ul>
     *   <li>幂等：{@code (userId, DECAY, dateBizId)} 唯一索引兜底「每日每用户最多一次衰减」；</li>
     *   <li>CAS：当前值不等于 {@code expected}（并发变动）时跳过，次日扫描自然追上；</li>
     *   <li>不刷新 {@code lastActiveAt}：衰减不是活跃行为，否则连续衰减会被自身重置打断；</li>
     *   <li>衰减后统一走 {@link #recomputeLevel} 重算等级，低于阈值即降级并通知。</li>
     * </ul>
     *
     * @param dateBizId 衰减日期（如 {@code 2026-10-23}），承担幂等键
     * @return 是否实际发生衰减（已衰减过 / 无变化 / CAS 失败均返回 false）
     */
    @Transactional
    public boolean applyDecay(Long userId, int expected, int target, String dateBizId, String remark) {
        if (userId == null || dateBizId == null || dateBizId.isBlank() || target >= expected) {
            return false;
        }
        try {
            if (alreadyLogged(userId, BIZ_DECAY, dateBizId)) {
                return false;
            }
            UserGrowth g = userGrowthMapper.selectOne(new LambdaQueryWrapper<UserGrowth>()
                    .eq(UserGrowth::getUserId, userId));
            if (g == null || valueOf(g) != expected) {
                return false;
            }
            if (!decayCas(g.getId(), expected, target)) {
                return false;
            }
            writeLog(userId, BIZ_DECAY, dateBizId, target - expected, expected, target, null,
                    remark == null ? "不活跃衰减" : remark);
            recomputeLevel(userId);
            metrics.increment("growth.decay");
            notify(userId, NotificationType.SYSTEM_ALERT, "成长值衰减",
                    "因长时间未活跃，成长值衰减 " + (expected - target) + " 点（剩余 " + target + "）");
            return true;
        } catch (Exception e) {
            metrics.increment("growth.decay.error");
            return false;
        }
    }

    /** 标记「即将衰减」已通知（REQ-05 提前通知当日去重，避免每日重复打扰）。 */
    @Transactional
    public boolean markDecayNotified(Long userId) {
        if (userId == null) {
            return false;
        }
        return userGrowthMapper.update(null, new LambdaUpdateWrapper<UserGrowth>()
                .eq(UserGrowth::getUserId, userId)
                .set(UserGrowth::getDecayNotifiedAt, LocalDateTime.now())) > 0;
    }

    /** 等级配置（按阈值升序），供控制器与降级计算复用。 */
    public List<MemberLevel> tiers() {
        List<MemberLevel> all = memberLevelMapper.selectList(null);
        all.sort(Comparator.comparingInt(t -> t.getMinGrowth() == null ? 0 : t.getMinGrowth()));
        return all;
    }

    /** 暴露配置（前端展示规则说明）。 */
    public IdlefishProperties.Growth getConfig() {
        return growth;
    }

    // ===== 内部：统一发放入口 =====

    /**
     * 统一获得入口：开关校验 → 冻结校验 → 防刷识别 → 幂等校验 → CAS 累计 → 流水 → 等级重算。
     *
     * @param delta      本次应得成长值（&le;0 直接返回 0）
     * @param amountFen  交易金额（分）；非交易来源传 0，跳过金额门槛判定
     * @param antiFraud  是否执行刷分识别（回补类来源不重复识别）
     */
    private int earn(Long userId, String bizType, String bizId, int delta, long amountFen, String remark) {
        return earn(userId, bizType, bizId, delta, amountFen, remark, true);
    }

    private int earn(Long userId, String bizType, String bizId, int delta, long amountFen, String remark,
                     boolean antiFraud) {
        if (!growth.isEnabled() || userId == null || delta <= 0) {
            return 0;
        }
        try {
            UserGrowth g = ensure(userId);
            int before = valueOf(g);

            // 1) 发放通道冻结：fail-closed，留 0 增量流水（REQ-03 / REQ-09）
            if (g.isFrozen()) {
                writeLog(userId, bizType, bizId, 0, before, before, g.getLevelCode(), "FROZEN_SKIP");
                metrics.increment("growth.earn.frozen");
                return 0;
            }
            // 2) 刷分识别（REQ-03）
            if (antiFraud) {
                GrowthAntiFraud.Decision d = GrowthAntiFraud.evaluate(
                        buildAntiFraudInput(userId, amountFen));
                if (d.blocked()) {
                    if (d.freeze()) {
                        freeze(userId, "疑似刷分（" + d.ruleCodesText() + "）");
                    }
                    writeLog(userId, bizType, bizId, 0, before, before, g.getLevelCode(),
                            "ANTIFRAUD_BLOCKED:" + d.ruleCodesText());
                    metrics.increment("growth.antifraud.block");
                    notify(userId, NotificationType.SYSTEM_ALERT, "成长值发放受限",
                            "本次未发放成长值（疑似刷分：" + d.ruleCodesText() + "）");
                    return 0;
                }
            }
            // 3) 幂等：同一来源 + 关联单号只发一次（唯一索引兜底前的预检）
            if (alreadyLogged(userId, bizType, bizId)) {
                return 0;
            }
            // 4) CAS 累计（有限重试防并发丢失更新）
            int after = GrowthValueCalculator.applyDelta(before, delta, 0);
            boolean ok = false;
            for (int i = 0; i < MAX_RETRY && !ok; i++) {
                UserGrowth fresh = userGrowthMapper.selectOne(new LambdaQueryWrapper<UserGrowth>()
                        .eq(UserGrowth::getUserId, userId));
                if (fresh == null) {
                    return 0;
                }
                int cur = valueOf(fresh);
                int target = GrowthValueCalculator.applyDelta(cur, delta, 0);
                ok = casUpdate(fresh.getId(), cur, target, delta);
                after = target;
            }
            if (!ok) {
                metrics.increment("growth.earn.cas.fail");
                return 0;
            }
            // 5) 流水 + 等级重算
            writeLog(userId, bizType, bizId, delta, before, after, null, remark);
            applyLevel(userId, after);
            metrics.increment("growth.earn." + bizType.toLowerCase(java.util.Locale.ROOT));
            notify(userId, NotificationType.GROWTH_EARNED, "成长值到账",
                    "您获得 " + delta + " 点成长值（" + remark + "）");
            return delta;
        } catch (Exception e) {
            // 成长值累计失败不影响交易 / 评价 / 验货 / 维权主流程（best-effort）
            metrics.increment("growth.earn.error");
            return 0;
        }
    }

    /** 扣减内部实现（判责扣减 / 冲正共用）：带 CAS 与流水，不阻塞主流程。 */
    private int deductInternal(Long userId, String bizType, String bizId, int amount, String remark) {
        try {
            UserGrowth g = ensure(userId);
            int before = valueOf(g);
            int after = GrowthValueCalculator.penalty(before, amount, 0);
            int actual = before - after;
            if (actual <= 0) {
                return 0;
            }
            boolean ok = false;
            for (int i = 0; i < MAX_RETRY && !ok; i++) {
                UserGrowth fresh = userGrowthMapper.selectOne(new LambdaQueryWrapper<UserGrowth>()
                        .eq(UserGrowth::getUserId, userId));
                if (fresh == null) {
                    return 0;
                }
                int cur = valueOf(fresh);
                int target = GrowthValueCalculator.penalty(cur, amount, 0);
                ok = casUpdate(fresh.getId(), cur, target, 0);
                after = target;
                actual = cur - target;
            }
            if (!ok) {
                return 0;
            }
            writeLog(userId, bizType, bizId, -actual, before, after, null, remark);
            applyLevel(userId, after);
            metrics.increment("growth.deduct." + bizType.toLowerCase(java.util.Locale.ROOT));
            return actual;
        } catch (Exception e) {
            metrics.increment("growth.deduct.error");
            return 0;
        }
    }

    // ===== 内部：等级重算（REQ-04 跨阈值即时升级）=====

    /**
     * 按最新成长值重算等级：编码变化即更新 {@code level_code} 与 {@code level_effective_at}，
     * 并区分升级 / 降级埋点与通知。跨级（一次跳多级）由 resolveLevelCode 直接落到目标级，
     * 进度与通知以最终等级为准。
     */
    private void applyLevel(Long userId, int growth) {
        try {
            UserGrowth g = userGrowthMapper.selectOne(new LambdaQueryWrapper<UserGrowth>()
                    .eq(UserGrowth::getUserId, userId));
            if (g == null) {
                return;
            }
            String before = g.getLevelCode();
            String after = GrowthValueCalculator.resolveLevelCode(tiers(), growth,
                    before == null ? DEFAULT_LEVEL : before);
            if (!GrowthValueCalculator.levelChanged(before, after)) {
                return;
            }
            userGrowthMapper.update(null, new LambdaUpdateWrapper<UserGrowth>()
                    .eq(UserGrowth::getId, g.getId())
                    .set(UserGrowth::getLevelCode, after)
                    .set(UserGrowth::getLevelEffectiveAt, LocalDateTime.now()));

            boolean upgrade = isUpgrade(before, after);
            metrics.increment(upgrade ? "growth.level.upgrade" : "growth.level.downgrade");
            if (upgrade) {
                notify(userId, NotificationType.GROWTH_LEVEL_UPGRADED, "会员等级提升",
                        "恭喜您升级到 " + after + "，更多权益已解锁");
            } else {
                notify(userId, NotificationType.SYSTEM_ALERT, "会员等级调整",
                        "您的会员等级已调整为 " + after);
            }
        } catch (Exception ignore) {
            // 等级重算失败不回滚已累计的成长值
        }
    }

    /** 判定升级 / 降级：比较两级阈值，阈值升高为升级；配置缺失时按「编码序号」保守判升级。 */
    private boolean isUpgrade(String beforeCode, String afterCode) {
        List<MemberLevel> tiers = tiers();
        int b = minGrowthOf(tiers, beforeCode);
        int a = minGrowthOf(tiers, afterCode);
        if (b == Integer.MIN_VALUE || a == Integer.MIN_VALUE) {
            return true;
        }
        return a >= b;
    }

    private int minGrowthOf(List<MemberLevel> tiers, String code) {
        if (code == null) {
            return Integer.MIN_VALUE;
        }
        return tiers.stream()
                .filter(t -> code.equals(t.getLevelCode()))
                .map(t -> t.getMinGrowth() == null ? 0 : t.getMinGrowth())
                .findFirst()
                .orElse(Integer.MIN_VALUE);
    }

    // ===== 内部：防刷输入装配 =====

    /**
     * 装配防刷输入：关联账户数（同设备其他账号，排除自身）+ 窗口内发放次数 + 本次金额。
     */
    private GrowthAntiFraud.Input buildAntiFraudInput(Long userId, long amountFen) {
        int linked = countLinkedAccounts(userId);
        int earnCount = countEarnInWindow(userId);
        return new GrowthAntiFraud.Input(linked, growth.getMaxLinkedAccounts(),
                earnCount, growth.getMaxEarnInWindow(), amountFen, growth.getMinAmountFen(), false);
    }

    /** 同一设备绑定的其他账号数（排除自身）；无设备指纹时按 1（仅自己）处理。 */
    private int countLinkedAccounts(Long userId) {
        List<DeviceFingerprint> mine = deviceFingerprintMapper.selectList(new LambdaQueryWrapper<DeviceFingerprint>()
                .eq(DeviceFingerprint::getUserId, userId));
        if (mine.isEmpty()) {
            return 0;
        }
        Set<String> deviceIds = mine.stream()
                .map(DeviceFingerprint::getDeviceId)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        if (deviceIds.isEmpty()) {
            return 0;
        }
        List<DeviceFingerprint> same = deviceFingerprintMapper.selectList(new LambdaQueryWrapper<DeviceFingerprint>()
                .in(DeviceFingerprint::getDeviceId, deviceIds));
        return (int) same.stream()
                .map(DeviceFingerprint::getUserId)
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(userId))
                .distinct()
                .count();
    }

    /** 窗口内已获得成长值的发放次数（含被拦截的 0 增量流水，从严计数）。 */
    private int countEarnInWindow(Long userId) {
        int days = Math.max(1, growth.getEarnWindowDays());
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        Long cnt = growthValueLogMapper.selectCount(new LambdaQueryWrapper<GrowthValueLog>()
                .eq(GrowthValueLog::getUserId, userId)
                .ge(GrowthValueLog::getCreatedAt, from)
                .ne(GrowthValueLog::getDelta, 0));
        return cnt == null ? 0 : cnt.intValue();
    }

    // ===== 内部：持久化辅助 =====

    /** 查询或创建账户（惰性创建，初始 L1 / 0 成长值）。 */
    private UserGrowth ensure(Long userId) {
        UserGrowth g = userGrowthMapper.selectOne(new LambdaQueryWrapper<UserGrowth>()
                .eq(UserGrowth::getUserId, userId));
        if (g != null) {
            return g;
        }
        UserGrowth created = new UserGrowth();
        created.setUserId(userId);
        created.setGrowthValue(0);
        created.setTotalEarned(0);
        created.setLevelCode(DEFAULT_LEVEL);
        created.setGrowthFrozen(0);
        userGrowthMapper.insert(created);
        return created;
    }

    /**
     * CAS 更新成长值：仅当当前值等于 {@code expected} 时才写入，杜绝并发丢失更新。
     *
     * @param earnDelta 累计获得量（&le;0 时不动 total_earned）
     */
    private boolean casUpdate(Long id, int expected, int target, int earnDelta) {
        LambdaUpdateWrapper<UserGrowth> uw = new LambdaUpdateWrapper<UserGrowth>()
                .eq(UserGrowth::getId, id)
                .eq(UserGrowth::getGrowthValue, expected)
                .set(UserGrowth::getGrowthValue, target)
                .set(UserGrowth::getLastActiveAt, LocalDateTime.now());
        if (earnDelta > 0) {
            uw.setSql("total_earned = total_earned + " + earnDelta);
        }
        return userGrowthMapper.update(null, uw) > 0;
    }

    /**
     * 衰减专用 CAS：只改成长值，<b>不刷新 lastActiveAt</b>（衰减不是活跃行为）。
     */
    private boolean decayCas(Long id, int expected, int target) {
        return userGrowthMapper.update(null, new LambdaUpdateWrapper<UserGrowth>()
                .eq(UserGrowth::getId, id)
                .eq(UserGrowth::getGrowthValue, expected)
                .set(UserGrowth::getGrowthValue, target)) > 0;
    }

    /** 同一 (用户, 来源, 关联单号) 是否已有流水（幂等预检）。 */
    private boolean alreadyLogged(Long userId, String bizType, String bizId) {
        if (userId == null || bizId == null || bizId.isBlank()) {
            return false;
        }
        Long cnt = growthValueLogMapper.selectCount(new LambdaQueryWrapper<GrowthValueLog>()
                .eq(GrowthValueLog::getUserId, userId)
                .eq(GrowthValueLog::getBizType, bizType)
                .eq(GrowthValueLog::getBizId, bizId));
        return cnt != null && cnt > 0;
    }

    /** 写成长值流水（REQ-09 全量留痕；唯一索引承担并发幂等兜底）。 */
    private void writeLog(Long userId, String bizType, String bizId, int delta, int before, int after,
                          String levelCode, String remark) {
        GrowthValueLog log = new GrowthValueLog();
        log.setUserId(userId);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setDelta(delta);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setLevelCode(levelCode);
        log.setRuleVersion(growth.getRuleVersion());
        log.setFrozen(0);
        log.setRemark(safe(remark));
        growthValueLogMapper.insert(log);
    }

    private int valueOf(UserGrowth g) {
        return g.getGrowthValue() == null ? 0 : g.getGrowthValue();
    }

    private String safe(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.length() > 480 ? s.substring(0, 480) : s;
    }

    private void notify(Long userId, NotificationType type, String title, String content) {
        try {
            notificationService.notify(userId, type, String.valueOf(userId), title, content);
        } catch (Exception ignored) {
            // 通知 best-effort，不影响成长值主流程
        }
    }

    private GrowthValueLogVO toVO(GrowthValueLog l) {
        GrowthValueLogVO v = new GrowthValueLogVO();
        v.setId(l.getId());
        v.setUserId(l.getUserId());
        v.setBizType(l.getBizType());
        v.setBizId(l.getBizId());
        v.setDelta(l.getDelta());
        v.setBeforeValue(l.getBeforeValue());
        v.setAfterValue(l.getAfterValue());
        v.setLevelCode(l.getLevelCode());
        v.setRuleVersion(l.getRuleVersion());
        v.setRemark(l.getRemark());
        v.setCreatedAt(l.getCreatedAt() == null ? null : l.getCreatedAt().format(DateTimeUtil.FMT));
        return v;
    }

    /** 账户 → 用户端 / 后台共用视图：等级解析走 MemberLevelCalculator（与 F-13.2 同口径）。 */
    private GrowthAccountVO toAccountVO(UserGrowth g, boolean preferStoredLevel) {
        List<MemberLevel> tiers = tiers();
        int gv = valueOf(g);
        MemberLevelCalculator.Resolved r = MemberLevelCalculator.resolve(tiers, gv);
        GrowthAccountVO v = new GrowthAccountVO();
        // preferStoredLevel=true（用户端）：展示已生效的 level_code，与等级通知口径一致；
        // false（后台只读）：直接以解析结果为准，避免展示尚未重算的滞后编码。
        String code = preferStoredLevel && g.getLevelCode() != null ? g.getLevelCode()
                : (r.current == null ? g.getLevelCode() : r.current.getLevelCode());
        v.setLevelCode(code);
        v.setGrowthValue(gv);
        v.setTotalEarned(g.getTotalEarned() == null ? 0 : g.getTotalEarned());
        if (r.current != null) {
            v.setLevelName(r.current.getLevelName());
            v.setIcon(r.current.getIcon());
            v.setColor(r.current.getColor());
            v.setFreeShipping(r.current.getFreeShipping());
            v.setPriorityReview(r.current.getPriorityReview());
            v.setCommissionDiscount(r.current.getCommissionDiscount());
        }
        v.setNextLevelName(r.next == null ? null : r.next.getLevelName());
        v.setGrowthToNext(r.growthToNext);
        v.setPercent(r.percent);
        v.setFrozen(g.isFrozen());
        v.setDaysUntilDecay(GrowthValueCalculator.daysUntilDecay(inactiveDaysOf(g), growth.getDecayThresholdDays()));
        v.setDecayNotifyDays(growth.getDecayNotifyDays());
        v.setRuleVersion(growth.getRuleVersion());
        return v;
    }

    /** 账户 → 后台列表视图（派生等级名称与距衰减天数）。 */
    private GrowthAccountAdminVO toAdminVO(UserGrowth g, List<MemberLevel> tiers) {
        GrowthAccountAdminVO v = new GrowthAccountAdminVO();
        v.setUserId(g.getUserId());
        v.setLevelCode(g.getLevelCode());
        v.setGrowthValue(valueOf(g));
        v.setTotalEarned(g.getTotalEarned() == null ? 0 : g.getTotalEarned());
        v.setFrozen(g.isFrozen());
        v.setLastActiveAt(g.getLastActiveAt() == null ? null : g.getLastActiveAt().format(DateTimeUtil.FMT));
        v.setDaysUntilDecay(GrowthValueCalculator.daysUntilDecay(inactiveDaysOf(g), growth.getDecayThresholdDays()));
        String name = tiers.stream()
                .filter(t -> t.getLevelCode() != null && t.getLevelCode().equals(g.getLevelCode()))
                .map(MemberLevel::getLevelName)
                .findFirst()
                .orElse(null);
        v.setLevelName(name);
        return v;
    }
}
