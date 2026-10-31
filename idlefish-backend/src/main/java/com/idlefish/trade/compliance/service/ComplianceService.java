package com.idlefish.trade.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.DateTimeUtil;
import com.idlefish.trade.compliance.ConsentItem;
import com.idlefish.trade.compliance.MinorGuard;
import com.idlefish.trade.compliance.entity.ConsentRecord;
import com.idlefish.trade.compliance.entity.UserCompliance;
import com.idlefish.trade.compliance.mapper.ConsentRecordMapper;
import com.idlefish.trade.compliance.mapper.UserComplianceMapper;
import com.idlefish.trade.compliance.vo.ComplianceProfileVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合规服务（ITER-U05）：个人信息授权、未成年人保护、营销通知偏好、关怀模式偏好。
 *
 * <p>设计约定：
 * <ul>
 *   <li>缺省语义：无档案记录 = 非未成年 + 允许营销通知 + 关闭关怀模式，避免把「数据缺失」误判为
 *       限制条件而阻断正常交易；一旦明确落值即严格生效。</li>
 *   <li>营销通知查询失败时 <b>fail-closed</b>（不下发营销消息），因为该判定只影响营销类触达，
 *       宁可漏发也不可违规下发（REQ-09/REQ-10）。</li>
 *   <li>未成年人/授权守卫仅在明确读到限制标记时拒绝，且拒绝原因明确可读（REQ-06，禁止静默拒绝）。</li>
 * </ul>
 */
@Service
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    private final UserComplianceMapper complianceMapper;
    private final ConsentRecordMapper consentMapper;
    private final MetricsRegistry metrics;

    public ComplianceService(UserComplianceMapper complianceMapper, ConsentRecordMapper consentMapper,
                             MetricsRegistry metrics) {
        this.complianceMapper = complianceMapper;
        this.consentMapper = consentMapper;
        this.metrics = metrics;
    }

    /** 合规中心视图：档案 + 全部授权项状态（未做选择时 granted 为 null）。 */
    public ComplianceProfileVO profile(Long userId) {
        UserCompliance uc = load(userId);
        Map<String, ConsentRecord> byCode = consentMap(userId);
        List<ComplianceProfileVO.ConsentVO> consents = new ArrayList<>();
        for (ConsentItem item : ConsentItem.values()) {
            ConsentRecord r = byCode.get(item.getCode());
            consents.add(ComplianceProfileVO.ConsentVO.of(item,
                    r == null ? null : r.getGranted() != null && r.getGranted() == 1,
                    r == null || r.getGrantedAt() == null ? null : r.getGrantedAt().format(DateTimeUtil.FMT),
                    r == null || r.getRevokedAt() == null ? null : r.getRevokedAt().format(DateTimeUtil.FMT)));
        }
        ComplianceProfileVO vo = new ComplianceProfileVO();
        vo.setUserId(userId);
        vo.setMinor(uc.getMinorFlag() != null && uc.getMinorFlag() == 1);
        vo.setAgeBand(uc.getAgeBand() == null ? "UNKNOWN" : uc.getAgeBand());
        vo.setDailyLimitMinutes(uc.getDailyLimitMinutes() == null ? MinorGuard.DEFAULT_DAILY_MINUTES
                : uc.getDailyLimitMinutes());
        vo.setUsedMinutes(currentUsedMinutes(uc));
        vo.setMarketingNotifyEnabled(uc.getMarketingNotify() == null || uc.getMarketingNotify() == 1);
        vo.setCareModeEnabled(uc.getCareMode() != null && uc.getCareMode() == 1);
        vo.setConsents(consents);
        return vo;
    }

    /** 授予授权（REQ-07）：幂等，重复授予仅刷新 grantedAt。 */
    public void grantConsent(Long userId, String consentCode) {
        ConsentItem item = ConsentItem.fromCode(consentCode);
        ConsentRecord exist = findConsent(userId, item.getCode());
        LocalDateTime now = LocalDateTime.now();
        if (exist == null) {
            ConsentRecord r = new ConsentRecord();
            r.setUserId(userId);
            r.setConsentCode(item.getCode());
            r.setGranted(1);
            r.setGrantedAt(now);
            consentMapper.insert(r);
        } else {
            ConsentRecord upd = new ConsentRecord();
            upd.setId(exist.getId());
            upd.setGranted(1);
            upd.setGrantedAt(now);
            upd.setRevokedAt(null);
            consentMapper.updateById(upd);
        }
        // 授予营销推送授权 = 打开营销通知开关，二者保持单一真源
        if (item == ConsentItem.MARKETING_PUSH) {
            setMarketingNotify(userId, true);
        }
        metrics.increment("compliance.consent.granted");
    }

    /**
     * 撤回授权（REQ-08）：停止基于该授权的处理，相关功能不可用（不静默继续使用）。
     * 撤回营销推送授权同时关闭营销通知开关。
     */
    public void revokeConsent(Long userId, String consentCode) {
        ConsentItem item = ConsentItem.fromCode(consentCode);
        ConsentRecord exist = findConsent(userId, item.getCode());
        LocalDateTime now = LocalDateTime.now();
        if (exist == null) {
            ConsentRecord r = new ConsentRecord();
            r.setUserId(userId);
            r.setConsentCode(item.getCode());
            r.setGranted(0);
            r.setRevokedAt(now);
            consentMapper.insert(r);
        } else {
            ConsentRecord upd = new ConsentRecord();
            upd.setId(exist.getId());
            upd.setGranted(0);
            upd.setRevokedAt(now);
            consentMapper.updateById(upd);
        }
        if (item == ConsentItem.MARKETING_PUSH) {
            setMarketingNotify(userId, false);
        }
        metrics.increment("compliance.consent.revoked");
    }

    /** 营销类通知开关（REQ-09）。 */
    public void setMarketingNotify(Long userId, boolean enabled) {
        UserCompliance uc = load(userId);
        UserCompliance upd = new UserCompliance();
        upd.setId(uc.getId());
        upd.setMarketingNotify(enabled ? 1 : 0);
        complianceMapper.updateById(upd);
    }

    /** 关怀模式开关（REQ-02，适老化偏好持久化）。 */
    public void setCareMode(Long userId, boolean enabled) {
        UserCompliance uc = load(userId);
        UserCompliance upd = new UserCompliance();
        upd.setId(uc.getId());
        upd.setCareMode(enabled ? 1 : 0);
        complianceMapper.updateById(upd);
    }

    /**
     * 实名/证照识别后标记未成年人（后台或实名回执调用）。
     *
     * @param age 真实年龄，null 表示未识别（置 UNKNOWN 且解除未成年标记）
     */
    public void markMinor(Long userId, Integer age) {
        UserCompliance uc = load(userId);
        boolean minor = MinorGuard.isMinor(age);
        UserCompliance upd = new UserCompliance();
        upd.setId(uc.getId());
        upd.setMinorFlag(minor ? 1 : 0);
        upd.setAgeBand(MinorGuard.ageBand(age));
        complianceMapper.updateById(upd);
        metrics.increment(minor ? "compliance.minor.marked" : "compliance.minor.cleared");
    }

    /**
     * 营销类通知是否允许下发（REQ-09/REQ-10）。无档案视为允许；查询异常时 fail-closed（返回 false）。
     */
    public boolean marketingNotifyEnabled(Long userId) {
        if (userId == null) {
            return true;
        }
        try {
            UserCompliance uc = complianceMapper.selectOne(new LambdaQueryWrapper<UserCompliance>()
                    .eq(UserCompliance::getUserId, userId));
            if (uc == null || uc.getMarketingNotify() == null) {
                return true;
            }
            return uc.getMarketingNotify() == 1;
        } catch (Exception e) {
            log.warn("[compliance] 营销通知偏好查询失败，按不下发处理 userId={}", userId, e);
            return false;
        }
    }

    /**
     * 未成年人保护守卫（REQ-04/REQ-05/REQ-06）：受限时抛 {@link Code#MINOR_RESTRICTED}，
     * 拒绝原因随异常 message 一并透出，供前端明确告知与引导监护人确认。
     */
    public void assertActionAllowed(Long userId, MinorGuard.RestrictedAction action) {
        if (userId == null || action == null) {
            return;
        }
        UserCompliance uc;
        try {
            uc = complianceMapper.selectOne(new LambdaQueryWrapper<UserCompliance>()
                    .eq(UserCompliance::getUserId, userId));
        } catch (Exception e) {
            // 合规判定不可读取时按「非未成年」放行，避免基础设施抖动阻断正常交易
            log.warn("[compliance] 合规档案读取失败，按非未成年放行 userId={}", userId, e);
            return;
        }
        if (uc == null || uc.getMinorFlag() == null || uc.getMinorFlag() != 1) {
            return;
        }
        int used = currentUsedMinutes(uc);
        int limit = uc.getDailyLimitMinutes() == null ? MinorGuard.DEFAULT_DAILY_MINUTES : uc.getDailyLimitMinutes();
        String reason = MinorGuard.denyReason(true, action, LocalTime.now(), used, limit);
        if (reason != null) {
            metrics.increment("compliance.minor.blocked");
            throw new BizException(Code.MINOR_RESTRICTED, reason);
        }
    }

    /**
     * 授权守卫（REQ-08）：仅在用户**明确撤回**该项授权时拒绝，并告知需重新授权。
     * 未落记录（历史存量用户尚未做出选择）沿用既有默认放行，避免存量交易被无差别阻断。
     */
    public void requireConsent(Long userId, ConsentItem item) {
        if (userId == null || item == null) {
            return;
        }
        try {
            ConsentRecord r = findConsent(userId, item.getCode());
            if (r != null && r.getGranted() != null && r.getGranted() == 0) {
                throw new BizException(Code.CONSENT_REVOKED,
                        "已撤回「" + item.getDesc() + "」授权，" + item.getDesc() + "相关功能暂不可用；可在「我的-合规中心」重新授权");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[compliance] 授权记录读取失败，按已授权放行 userId={} item={}", userId, item.getCode(), e);
        }
    }

    /** 累积未成年人当日使用时长（REQ-05），跨日自动清零。 */
    public void addUsedMinutes(Long userId, int minutes) {
        if (userId == null || minutes <= 0) {
            return;
        }
        UserCompliance uc = load(userId);
        LocalDate today = LocalDate.now();
        int base = today.equals(uc.getActiveDate()) && uc.getUsedMinutes() != null ? uc.getUsedMinutes() : 0;
        UserCompliance upd = new UserCompliance();
        upd.setId(uc.getId());
        upd.setUsedMinutes(base + minutes);
        upd.setActiveDate(today);
        complianceMapper.updateById(upd);
    }

    /** 读取档案，不存在则按默认语义创建（非未成年 + 允许营销 + 关闭关怀）。 */
    private UserCompliance load(Long userId) {
        UserCompliance uc = complianceMapper.selectOne(new LambdaQueryWrapper<UserCompliance>()
                .eq(UserCompliance::getUserId, userId));
        if (uc != null) {
            return uc;
        }
        UserCompliance created = new UserCompliance();
        created.setUserId(userId);
        created.setMinorFlag(0);
        created.setAgeBand("UNKNOWN");
        created.setMarketingNotify(1);
        created.setCareMode(0);
        created.setDailyLimitMinutes(MinorGuard.DEFAULT_DAILY_MINUTES);
        created.setUsedMinutes(0);
        created.setActiveDate(LocalDate.now());
        complianceMapper.insert(created);
        return created;
    }

    private ConsentRecord findConsent(Long userId, String code) {
        return consentMapper.selectOne(new LambdaQueryWrapper<ConsentRecord>()
                .eq(ConsentRecord::getUserId, userId)
                .eq(ConsentRecord::getConsentCode, code)
                .orderByDesc(ConsentRecord::getId)
                .last("LIMIT 1"));
    }

    private Map<String, ConsentRecord> consentMap(Long userId) {
        List<ConsentRecord> list = consentMapper.selectList(new LambdaQueryWrapper<ConsentRecord>()
                .eq(ConsentRecord::getUserId, userId));
        Map<String, ConsentRecord> map = new HashMap<>();
        for (ConsentRecord r : list) {
            map.putIfAbsent(r.getConsentCode(), r);
        }
        return map;
    }

    /** 当日已用时长（跨日归零）。 */
    private int currentUsedMinutes(UserCompliance uc) {
        if (uc.getUsedMinutes() == null) {
            return 0;
        }
        return LocalDate.now().equals(uc.getActiveDate()) ? uc.getUsedMinutes() : 0;
    }
}
