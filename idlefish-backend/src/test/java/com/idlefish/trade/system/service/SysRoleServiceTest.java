package com.idlefish.trade.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.batch.BatchInsertService;
import com.idlefish.trade.system.entity.RoleMenu;
import com.idlefish.trade.system.mapper.RoleMenuMapper;
import com.idlefish.trade.system.mapper.SysRoleMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysRoleService.assignMenus 重构后行为验证（D3 债务修复）：
 * 循环 insert 改为一次性批量插入，删除+批量插入的语义与入参保持不变。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysRoleServiceTest {

    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private RoleMenuMapper roleMenuMapper;
    @Mock
    private AdminUserRoleMapper adminUserRoleMapper;
    @Mock
    private BatchInsertService batchInsertService;

    @InjectMocks
    private SysRoleService service;

    @Test
    void assignMenus_delegatesToBatchInsert_withAllRelations() {
        when(batchInsertService.batchInsert(eq(RoleMenu.class), eq(RoleMenuMapper.class), any(List.class)))
                .thenReturn(2);

        service.assignMenus(5L, List.of(100L, 200L));

        verify(roleMenuMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<List<RoleMenu>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchInsertService).batchInsert(eq(RoleMenu.class), eq(RoleMenuMapper.class), captor.capture());
        List<RoleMenu> relations = captor.getValue();
        assertEquals(2, relations.size());
        for (int i = 0; i < relations.size(); i++) {
            RoleMenu rm = relations.get(i);
            assertEquals(5L, rm.getRoleId());
            assertEquals(100L + i * 100L, rm.getMenuId());
        }
    }

    @Test
    void assignMenus_emptyMenus_skipsBatchInsert() {
        service.assignMenus(5L, List.of());

        verify(roleMenuMapper).delete(any(LambdaQueryWrapper.class));
        verify(batchInsertService, never()).batchInsert(any(), any(), any());
    }

    @Test
    void assignMenus_nullMenus_skipsBatchInsert() {
        service.assignMenus(5L, null);

        verify(roleMenuMapper).delete(any(LambdaQueryWrapper.class));
        verify(batchInsertService, never()).batchInsert(any(), any(), any());
    }
}
