package com.idlefish.trade.compliance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.compliance.ConsentItem;
import com.idlefish.trade.compliance.MinorGuard;
import com.idlefish.trade.compliance.entity.ConsentRecord;
import com.idlefish.trade.compliance.entity.UserCompliance;
import com.idlefish.trade.compliance.mapper.ConsentRecordMapper;
import com.idlefish.trade.compliance.mapper.UserComplianceMapper;
import com.idlefish.trade.compliance.vo.ComplianceProfileVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合规服务单测（ITER-U05）：授权授予/撤回、未成年人守卫、营销通知 fail-closed、时长计量。
 *
 * <p>纯 Mockito 离线范式；走 MP Lambda 条件构造器，故须为每个涉及实体 initTableInfo。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComplianceServiceTest {

    private static final Long USER = 1L;

    @Mock private UserComplianceMapper complianceMapper;
    @Mock private ConsentRecordMapper consentMapper;
    @Mock private MetricsRegistry metrics;

    @InjectMocks
    private ComplianceService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "dummy");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, UserCompliance.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ConsentRecord.class);
    }

    private UserCompliance profile(boolean minor, int marketingNotify, int careMode, int used, LocalDate activeDate) {
        UserCompliance uc = new UserCompliance();
        uc.setId(10L);
        uc.setUserId(USER);
        uc.setMinorFlag(minor ? 1 : 0);
        uc.setAgeBand(minor ? "TEEN" : "ADULT");
        uc.setMarketingNotify(marketingNotify);
        uc.setCareMode(careMode);
        uc.setDailyLimitMinutes(MinorGuard.DEFAULT_DAILY_MINUTES);
        uc.setUsedMinutes(used);
        uc.setActiveDate(activeDate);
        return uc;
    }

    @Test
    void profile_defaults_when_no_record() {
        when(complianceMapper.selectOne(any())).thenReturn(null);
        when(consentMapper.selectList(any())).thenReturn(List.of());

        ComplianceProfileVO vo = service.profile(USER);
        assertEquals(USER, vo.getUserId());
        assertFalse(vo.getMinor());
        assertEquals("UNKNOWN", vo.getAgeBand());
        assertTrue(vo.getMarketingNotifyEnabled(), "无档案视为允许营销通知");
        assertFalse(vo.getCareModeEnabled());
        assertEquals(MinorGuard.DEFAULT_DAILY_MINUTES, vo.getDailyLimitMinutes());
        assertEquals(ConsentItem.values().length, vo.getConsents().size());
        // 未做出选择时 granted 为 null（不默认为已授权，也不默认为已撤回）
        assertTrue(vo.getConsents().stream().allMatch(c -> c.getGranted() == null));
    }

    @Test
    void grantConsent_inserts_and_marks_granted() {
        when(consentMapper.selectOne(any())).thenReturn(null);
        service.grantConsent(USER, "phone");

        ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
        verify(consentMapper).insert(captor.capture());
        assertEquals("phone", captor.getValue().getConsentCode());
        assertEquals(Integer.valueOf(1), captor.getValue().getGranted());
        assertNotNull(captor.getValue().getGrantedAt());
    }

    @Test
    void grantConsent_is_idempotent_and_refreshes_time() {
        ConsentRecord exist = new ConsentRecord();
        exist.setId(5L);
        exist.setUserId(USER);
        exist.setConsentCode("phone");
        exist.setGranted(1);
        exist.setGrantedAt(LocalDateTime.now().minusDays(3));
        when(consentMapper.selectOne(any())).thenReturn(exist);

        service.grantConsent(USER, "phone");

        ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
        verify(consentMapper).updateById(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        assertEquals(Integer.valueOf(1), captor.getValue().getGranted());
        assertTrue(captor.getValue().getGrantedAt().isAfter(exist.getGrantedAt()));
    }

    @Test
    void revokeConsent_records_revoked_at() {
        ConsentRecord exist = new ConsentRecord();
        exist.setId(5L);
        exist.setUserId(USER);
        exist.setConsentCode("address");
        exist.setGranted(1);
        when(consentMapper.selectOne(any())).thenReturn(exist);

        service.revokeConsent(USER, "address");

        ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
        verify(consentMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(0), captor.getValue().getGranted());
        assertNotNull(captor.getValue().getRevokedAt());
    }

    @Test
    void revoke_marketing_push_also_closes_marketing_notify() {
        when(complianceMapper.selectOne(any())).thenReturn(profile(false, 1, 0, 0, LocalDate.now()));
        when(consentMapper.selectOne(any())).thenReturn(null);

        service.revokeConsent(USER, "marketing_push");

        ArgumentCaptor<UserCompliance> captor = ArgumentCaptor.forClass(UserCompliance.class);
        verify(complianceMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(0), captor.getValue().getMarketingNotify());
    }

    @Test
    void requireConsent_rejects_when_explicitly_revoked() {
        ConsentRecord revoked = new ConsentRecord();
        revoked.setId(7L);
        revoked.setUserId(USER);
        revoked.setConsentCode("phone");
        revoked.setGranted(0);
        revoked.setRevokedAt(LocalDateTime.now());
        when(consentMapper.selectOne(any())).thenReturn(revoked);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireConsent(USER, ConsentItem.PHONE));
        assertEquals(Code.CONSENT_REVOKED.getCode(), ex.getCode());
    }

    @Test
    void requireConsent_passes_for_legacy_user_without_record() {
        when(consentMapper.selectOne(any())).thenReturn(null);
        service.requireConsent(USER, ConsentItem.PHONE);
        service.requireConsent(USER, ConsentItem.ADDRESS);
    }

    @Test
    void assertActionAllowed_blocks_minor_with_readable_reason() {
        when(complianceMapper.selectOne(any()))
                .thenReturn(profile(true, 1, 0, 0, LocalDate.now()));
        // 固定到非宵禁时段：LocalTime.now() 由断言侧的守卫内部取，故这里只验证「必然受限」的操作路径
        BizException ex = assertThrows(BizException.class,
                () -> service.assertActionAllowed(USER, MinorGuard.RestrictedAction.TRADE));
        assertEquals(Code.MINOR_RESTRICTED.getCode(), ex.getCode());
        assertNotNull(ex.getMessage());
        assertFalse(ex.getMessage().isBlank(), "拒绝原因必须可读，禁止静默拒绝");
    }

    @Test
    void assertActionAllowed_passes_for_adult() {
        when(complianceMapper.selectOne(any()))
                .thenReturn(profile(false, 1, 0, 0, LocalDate.now()));
        service.assertActionAllowed(USER, MinorGuard.RestrictedAction.TRADE);
        service.assertActionAllowed(USER, MinorGuard.RestrictedAction.PAY);
    }

    @Test
    void assertActionAllowed_fails_open_when_store_unavailable() {
        when(complianceMapper.selectOne(any())).thenThrow(new RuntimeException("db down"));
        // 合规判定不可读取时按「非未成年」放行，避免基础设施抖动阻断正常交易
        service.assertActionAllowed(USER, MinorGuard.RestrictedAction.TRADE);
    }

    @Test
    void marketingNotifyEnabled_fails_closed_on_error() {
        when(complianceMapper.selectOne(any())).thenThrow(new RuntimeException("db down"));
        assertFalse(service.marketingNotifyEnabled(USER), "营销通知判定异常时必须 fail-closed");
    }

    @Test
    void marketingNotifyEnabled_reads_preference() {
        when(complianceMapper.selectOne(any())).thenReturn(profile(false, 0, 0, 0, LocalDate.now()));
        assertFalse(service.marketingNotifyEnabled(USER));

        when(complianceMapper.selectOne(any())).thenReturn(profile(false, 1, 0, 0, LocalDate.now()));
        assertTrue(service.marketingNotifyEnabled(USER));

        // userId 为空（系统级广播）不做拦截
        assertTrue(service.marketingNotifyEnabled(null));
    }

    @Test
    void addUsedMinutes_resets_across_days() {
        UserCompliance uc = profile(true, 1, 0, 30, LocalDate.now().minusDays(1));
        when(complianceMapper.selectOne(any())).thenReturn(uc);

        service.addUsedMinutes(USER, 10);

        ArgumentCaptor<UserCompliance> captor = ArgumentCaptor.forClass(UserCompliance.class);
        verify(complianceMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(10), captor.getValue().getUsedMinutes(), "跨日应从 0 重新累积");
        assertEquals(LocalDate.now(), captor.getValue().getActiveDate());
    }

    @Test
    void addUsedMinutes_accumulates_same_day() {
        when(complianceMapper.selectOne(any())).thenReturn(profile(true, 1, 0, 30, LocalDate.now()));
        service.addUsedMinutes(USER, 10);
        ArgumentCaptor<UserCompliance> captor = ArgumentCaptor.forClass(UserCompliance.class);
        verify(complianceMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(40), captor.getValue().getUsedMinutes());
    }

    @Test
    void markMinor_updates_flag_and_age_band() {
        when(complianceMapper.selectOne(any())).thenReturn(profile(false, 1, 0, 0, LocalDate.now()));
        service.markMinor(USER, 15);
        ArgumentCaptor<UserCompliance> captor = ArgumentCaptor.forClass(UserCompliance.class);
        verify(complianceMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(1), captor.getValue().getMinorFlag());
        assertEquals("TEEN", captor.getValue().getAgeBand());
    }

    @Test
    void unknown_consent_code_rejected() {
        assertThrows(BizException.class, () -> service.grantConsent(USER, "not_exist"));
        assertThrows(BizException.class, () -> service.grantConsent(USER, null));
    }

    @Test
    void setters_write_preference_flags() {
        when(complianceMapper.selectOne(any())).thenReturn(profile(false, 1, 0, 0, LocalDate.now()));
        service.setCareMode(USER, true);
        ArgumentCaptor<UserCompliance> captor = ArgumentCaptor.forClass(UserCompliance.class);
        verify(complianceMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(1), captor.getValue().getCareMode());

        service.setMarketingNotify(USER, false);
        ArgumentCaptor<UserCompliance> c2 = ArgumentCaptor.forClass(UserCompliance.class);
        verify(complianceMapper, org.mockito.Mockito.times(2)).updateById(c2.capture());
        assertEquals(Integer.valueOf(0), c2.getValue().getMarketingNotify());
    }

    @Test
    void grant_and_revoke_emit_metrics() {
        when(consentMapper.selectOne(any())).thenReturn(null);
        service.grantConsent(USER, "phone");
        service.revokeConsent(USER, "phone");
        verify(metrics).increment("compliance.consent.granted");
        verify(metrics).increment("compliance.consent.revoked");
    }
}
