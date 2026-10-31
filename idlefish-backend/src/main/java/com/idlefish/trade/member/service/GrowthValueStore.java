package com.idlefish.trade.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.member.GrowthAntiFraud;
import com.idlefish.trade.member.entity.GrowthValueLog;
import com.idlefish.trade.member.entity.MemberLevel;
import com.idlefish.trade.member.entity.UserGrowth;
import com.idlefish.trade.member.mapper.GrowthValueLogMapper;
import com.idlefish.trade.risk.entity.DeviceFingerprint;
import com.idlefish.trade.risk.mapper.DeviceFingerprintMapper;
import com.idlefish.trade.member.mapper.MemberLevelMapper;
import com.idlefish.trade.member.mapper.UserGrowthMapper;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 成长值数据访问协作类（{@link GrowthValueService} 的内部协作对象，非独立 API）：
 * 承载账户惰性创建、CAS 累计 / 衰减、幂等预检、流水落库与防刷查询等纯数据访问逻辑，
 * 使服务类聚焦「编排 + 指标 + 通知」。由服务在构造期以既有 Mapper 实例化，不引入 Spring Bean。
 */
class GrowthValueStore {

    /** 账户默认等级（L1）。 */
    public static final String DEFAULT_LEVEL = "L1";

    private final UserGrowthMapper userGrowthMapper;
    private final GrowthValueLogMapper growthValueLogMapper;
    private final MemberLevelMapper memberLevelMapper;
    private final DeviceFingerprintMapper deviceFingerprintMapper;
    private final IdlefishProperties.Growth growth;

    GrowthValueStore(UserGrowthMapper userGrowthMapper,
                     GrowthValueLogMapper growthValueLogMapper,
                     MemberLevelMapper memberLevelMapper,
                     DeviceFingerprintMapper deviceFingerprintMapper,
                     IdlefishProperties.Growth growth) {
        this.userGrowthMapper = userGrowthMapper;
        this.growthValueLogMapper = growthValueLogMapper;
        this.memberLevelMapper = memberLevelMapper;
        this.deviceFingerprintMapper = deviceFingerprintMapper;
        this.growth = growth;
    }

    /** 查询或创建账户（惰性创建，初始 L1 / 0 成长值）。 */
    UserGrowth ensure(Long userId) {
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
    boolean casUpdate(Long id, int expected, int target, int earnDelta) {
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
    boolean decayCas(Long id, int expected, int target) {
        return userGrowthMapper.update(null, new LambdaUpdateWrapper<UserGrowth>()
                .eq(UserGrowth::getId, id)
                .eq(UserGrowth::getGrowthValue, expected)
                .set(UserGrowth::getGrowthValue, target)) > 0;
    }

    /** 同一 (用户, 来源, 关联单号) 是否已有流水（幂等预检）。 */
    boolean alreadyLogged(Long userId, String bizType, String bizId) {
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
    void writeLog(Long userId, String bizType, String bizId, int delta, int before, int after,
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
        log.setRemark(GrowthValueViews.safe(remark));
        growthValueLogMapper.insert(log);
    }

    /** 等级配置（按阈值升序），供控制器与降级计算复用。 */
    List<MemberLevel> tiers() {
        List<MemberLevel> all = memberLevelMapper.selectList(null);
        all.sort(Comparator.comparingInt(t -> t.getMinGrowth() == null ? 0 : t.getMinGrowth()));
        return all;
    }

    /**
     * 装配防刷输入：关联账户数（同设备其他账号，排除自身）+ 窗口内发放次数 + 本次金额。
     */
    GrowthAntiFraud.Input buildAntiFraudInput(Long userId, long amountFen) {
        int linked = countLinkedAccounts(userId);
        int earnCount = countEarnInWindow(userId);
        return new GrowthAntiFraud.Input(linked, growth.getMaxLinkedAccounts(),
                earnCount, growth.getMaxEarnInWindow(), amountFen, growth.getMinAmountFen(), false);
    }

    /** 同一设备绑定的其他账号数（排除自身）；无设备指纹时按 0（仅自己）处理。 */
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
}
