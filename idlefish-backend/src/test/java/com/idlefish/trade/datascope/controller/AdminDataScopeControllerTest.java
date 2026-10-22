package com.idlefish.trade.datascope.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.datascope.DataScope;
import com.idlefish.trade.datascope.DataScopeDecision;
import com.idlefish.trade.datascope.dto.DataScopeAdminDTO;
import com.idlefish.trade.datascope.entity.DataScopeTempGrant;
import com.idlefish.trade.datascope.service.DataScopeService;
import com.idlefish.trade.datascope.service.TempGrantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据范围管理后台控制器单测（ITER-T05）：直接调用方法，校验与 DataScopeService/TempGrantService 的接线。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDataScopeControllerTest {

    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private TempGrantService tempGrantService;

    @InjectMocks
    private AdminDataScopeController controller;

    private AdminUser admin() {
        AdminUser a = new AdminUser();
        a.setId(1L);
        a.setOrgId(1L);
        return a;
    }

    @Test
    void myScope_delegatesToService() {
        DataScopeDecision d = new DataScopeDecision(1L, 1L, DataScope.SELF, Set.of(1L));
        when(dataScopeService.resolveForAdmin(1L)).thenReturn(d);
        Result<DataScopeDecision> r = controller.myScope(admin());
        assertEquals(0, r.getCode());
        assertEquals(d, r.getData());
    }

    @Test
    void orgsInScope_delegatesToService() {
        when(dataScopeService.orgsInScope(1L)).thenReturn(Set.of(1L, 2L));
        Result<Set<Long>> r = controller.orgsInScope(admin());
        assertEquals(Set.of(1L, 2L), r.getData());
    }

    @Test
    void preview_delegatesToIsAccessible() {
        when(dataScopeService.isAccessible(1L, 9L)).thenReturn(true);
        DataScopeAdminDTO.PreviewRequest req = new DataScopeAdminDTO.PreviewRequest();
        req.targetOrgId = 9L;
        Result<Boolean> r = controller.preview(admin(), req);
        assertTrue(r.getData());
    }

    @Test
    void tempGrant_delegatesToCreate() {
        DataScopeTempGrant g = new DataScopeTempGrant();
        g.setGrantNo("GR1");
        when(tempGrantService.create(eq(1L), any(), any())).thenReturn(g);
        DataScopeAdminDTO.TempGrantRequest req = new DataScopeAdminDTO.TempGrantRequest();
        req.orgIds = List.of(5L, 6L);
        req.expiredAt = LocalDateTime.now().plusDays(1);
        Result<DataScopeTempGrant> r = controller.tempGrant(admin(), req);
        assertEquals("GR1", r.getData().getGrantNo());
    }

    @Test
    void tempGrants_delegatesToListActive() {
        when(tempGrantService.listActive(eq(1L), any())).thenReturn(List.of());
        Result<List<DataScopeTempGrant>> r = controller.tempGrants(admin());
        assertTrue(r.getData().isEmpty());
    }

    @Test
    void revoke_delegatesToRevoke() {
        controller.revoke(admin(), "GR1");
        verify(tempGrantService).revoke("GR1");
    }

    @Test
    void migrate_delegatesToMigrateData() {
        when(dataScopeService.migrateData(eq(1L), eq(2L), eq(3L))).thenReturn(4);
        DataScopeAdminDTO.MigrateRequest req = new DataScopeAdminDTO.MigrateRequest();
        req.fromOrgId = 2L;
        req.toOrgId = 3L;
        Result<Integer> r = controller.migrate(admin(), req);
        assertEquals(4, r.getData());
    }
}
