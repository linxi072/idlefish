package com.idlefish.trade.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.admin.mapper.AdminUserMapper;
import com.idlefish.trade.common.batch.BatchInsertService;
import com.idlefish.trade.system.entity.AdminUserRole;
import com.idlefish.trade.system.mapper.AdminUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysUserService.assignRoles 重构后行为验证（D2 债务修复）：
 * 循环 insert 改为一次性批量插入，删除+批量插入的语义与入参保持不变。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserServiceTest {

    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private AdminUserRoleMapper adminUserRoleMapper;
    @Mock
    private BatchInsertService batchInsertService;

    @InjectMocks
    private SysUserService service;

    @Test
    void assignRoles_delegatesToBatchInsert_withAllRelations() {
        when(batchInsertService.batchInsert(eq(AdminUserRole.class), eq(AdminUserRoleMapper.class), any(List.class)))
                .thenReturn(3);

        service.assignRoles(1L, List.of(10L, 20L, 30L));

        verify(adminUserRoleMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<List<AdminUserRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchInsertService).batchInsert(eq(AdminUserRole.class), eq(AdminUserRoleMapper.class), captor.capture());
        List<AdminUserRole> relations = captor.getValue();
        assertEquals(3, relations.size());
        for (int i = 0; i < relations.size(); i++) {
            AdminUserRole r = relations.get(i);
            assertEquals(1L, r.getAdminUserId());
            assertEquals(10L + i * 10L, r.getRoleId());
        }
    }

    @Test
    void assignRoles_emptyRoles_skipsBatchInsert() {
        service.assignRoles(1L, List.of());

        verify(adminUserRoleMapper).delete(any(LambdaQueryWrapper.class));
        verify(batchInsertService, never()).batchInsert(any(), any(), any());
    }

    @Test
    void assignRoles_nullRoles_skipsBatchInsert() {
        service.assignRoles(1L, null);

        verify(adminUserRoleMapper).delete(any(LambdaQueryWrapper.class));
        verify(batchInsertService, never()).batchInsert(any(), any(), any());
    }
}
