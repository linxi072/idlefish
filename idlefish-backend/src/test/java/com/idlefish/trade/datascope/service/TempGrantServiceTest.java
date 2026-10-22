package com.idlefish.trade.datascope.service;

import com.idlefish.trade.datascope.entity.DataScopeTempGrant;
import com.idlefish.trade.datascope.mapper.DataScopeTempGrantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 临时授权服务单测（ITER-T05 / REQ-05）：有效性判定、范围合并、生命周期。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TempGrantServiceTest {

    @Mock
    private DataScopeTempGrantMapper grantMapper;

    private TempGrantService service;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 22, 12, 0);

    @BeforeEach
    void setUp() {
        service = new TempGrantService(grantMapper);
    }

    @Test
    void isEffective_activeUnexpired_true() {
        DataScopeTempGrant g = new DataScopeTempGrant();
        g.setStatus(1);
        g.setExpiredAt(now.plusDays(1));
        assertTrue(service.isEffective(g, now));
    }

    @Test
    void isEffective_revoked_false() {
        DataScopeTempGrant g = new DataScopeTempGrant();
        g.setStatus(0);
        g.setExpiredAt(now.plusDays(1));
        assertFalse(service.isEffective(g, now));
    }

    @Test
    void isEffective_expired_false() {
        DataScopeTempGrant g = new DataScopeTempGrant();
        g.setStatus(1);
        g.setExpiredAt(now.minusMinutes(1));
        assertFalse(service.isEffective(g, now));
    }

    @Test
    void effectiveOrgIds_mergesGrantsIntoBase() {
        DataScopeTempGrant g = new DataScopeTempGrant();
        g.setStatus(1);
        g.setExpiredAt(now.plusDays(1));
        g.setOrgIds("50,60");
        Set<Long> base = Set.of(10L, 20L);
        Set<Long> merged = service.effectiveOrgIds(base, List.of(g), now);
        assertEquals(Set.of(10L, 20L, 50L, 60L), merged);
    }

    @Test
    void effectiveOrgIds_nullBase_staysUnrestricted() {
        DataScopeTempGrant g = new DataScopeTempGrant();
        g.setStatus(1);
        g.setExpiredAt(now.plusDays(1));
        g.setOrgIds("50");
        assert service.effectiveOrgIds(null, List.of(g), now) == null;
    }

    @Test
    void effectiveOrgIds_expiredGrantIgnored() {
        DataScopeTempGrant g = new DataScopeTempGrant();
        g.setStatus(1);
        g.setExpiredAt(now.minusMinutes(1));
        g.setOrgIds("50");
        assertEquals(Set.of(10L), service.effectiveOrgIds(Set.of(10L), List.of(g), now));
    }

    @Test
    void create_generatesGrantNoAndInserts() {
        DataScopeTempGrant created = service.create(7L, Set.of(11L, 12L), now.plusDays(3));
        assertTrue(created.getGrantNo() != null && created.getGrantNo().startsWith("GR"));
        assertEquals("11,12", created.getOrgIds());
        assertEquals(1, created.getStatus());
        ArgumentCaptor<DataScopeTempGrant> cap = ArgumentCaptor.forClass(DataScopeTempGrant.class);
        verify(grantMapper).insert(cap.capture());
        assertEquals(7L, cap.getValue().getAdminId());
    }

    @Test
    void revoke_setsStatusZeroWhenActive() {
        DataScopeTempGrant g = new DataScopeTempGrant();
        g.setGrantNo("GR1");
        g.setStatus(1);
        when(grantMapper.selectOne(any())).thenReturn(g);
        service.revoke("GR1");
        assertEquals(0, g.getStatus());
        verify(grantMapper).updateById((DataScopeTempGrant) g);
    }

    @Test
    void revoke_noopWhenMissing() {
        when(grantMapper.selectOne(any())).thenReturn(null);
        service.revoke("GRX");
        verify(grantMapper, never()).updateById(any(com.idlefish.trade.datascope.entity.DataScopeTempGrant.class));
    }

    @Test
    void parseOrgIds_toleratesBlanksAndInvalid() {
        assertEquals(Set.of(1L, 2L, 3L), TempGrantService.parseOrgIds("1, 2 , x ,3"));
        assertTrue(TempGrantService.parseOrgIds("").isEmpty());
    }
}
