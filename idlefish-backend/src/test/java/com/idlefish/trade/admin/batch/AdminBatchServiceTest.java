package com.idlefish.trade.admin.batch;

import com.idlefish.trade.admin.service.AdminAuthService;
import com.idlefish.trade.admin.service.AdminService;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.risk.entity.AuditLog;
import com.idlefish.trade.risk.mapper.AuditLogMapper;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量操作服务单测（ITER-U03）：预检跳过、部分失败不回滚、二次确认闸门、批次审计留痕。
 *
 * <p>纯 Mockito 离线范式。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBatchServiceTest {

    private static final String ROLE = "admin";
    private static final Long ADMIN = 9L;

    @Mock private AdminService adminService;
    @Mock private AdminAuthService adminAuthService;
    @Mock private ItemMapper itemMapper;
    @Mock private UserMapper userMapper;
    @Mock private AuditLogMapper auditLogMapper;
    @Mock private MetricsRegistry metrics;

    @InjectMocks
    private AdminBatchService service;

    private Item item(Long id, String auditStatus, Integer deleted) {
        Item it = new Item();
        it.setId(id);
        it.setAuditStatus(auditStatus);
        it.setDeleted(deleted);
        return it;
    }

    private User user(Long id, Integer status) {
        User u = new User();
        u.setId(id);
        u.setStatus(status);
        return u;
    }

    @Test
    void audit_all_success_and_records_batch_audit() {
        when(itemMapper.selectById(1L)).thenReturn(item(1L, "pending", 0));
        when(itemMapper.selectById(2L)).thenReturn(item(2L, "pending", 0));

        BatchResultVO vo = service.auditItems(ROLE, ADMIN, List.of(1L, 2L), true, null);

        assertEquals(2, vo.getTotal());
        assertEquals(2, vo.getSuccess());
        assertEquals(0, vo.getSkipped());
        assertEquals(0, vo.getFailed());
        verify(adminService).approve(ROLE, ADMIN, 1L);
        verify(adminService).approve(ROLE, ADMIN, 2L);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        assertEquals("batch:item:audit", captor.getValue().getAction());
        assertEquals(ADMIN, captor.getValue().getOperatorId());
        assertTrue(captor.getValue().getDetail().contains("success=2"), "审计须记录结果摘要");
    }

    @Test
    void audit_skips_missing_and_already_settled_items() {
        when(itemMapper.selectById(1L)).thenReturn(null);
        when(itemMapper.selectById(2L)).thenReturn(item(2L, "pending", 1));
        when(itemMapper.selectById(3L)).thenReturn(item(3L, "pass", 0));
        when(itemMapper.selectById(4L)).thenReturn(item(4L, "pending", 0));

        BatchResultVO vo = service.auditItems(ROLE, ADMIN, List.of(1L, 2L, 3L, 4L), true, null);

        assertEquals(4, vo.getTotal());
        assertEquals(1, vo.getSuccess());
        assertEquals(3, vo.getSkipped());
        assertEquals("商品不存在或已删除", vo.getItems().get(0).getReason());
        assertEquals("已是通过状态，跳过以避免重复通知卖家", vo.getItems().get(2).getReason());
        // 跳过项绝不产生副作用
        verify(adminService, never()).approve(eq(ROLE), eq(ADMIN), eq(3L));
    }

    @Test
    void audit_partial_failure_does_not_rollback() {
        when(itemMapper.selectById(1L)).thenReturn(item(1L, "pending", 0));
        when(itemMapper.selectById(2L)).thenReturn(item(2L, "pending", 0));
        doThrow(new BizException(Code.PARAM_INVALID, "状态不允许该流转"))
                .when(adminService).approve(ROLE, ADMIN, 2L);

        BatchResultVO vo = service.auditItems(ROLE, ADMIN, List.of(1L, 2L), true, null);

        assertEquals(1, vo.getSuccess());
        assertEquals(1, vo.getFailed());
        assertEquals(List.of("2"), vo.getFailedIds(), "失败 ID 必须可重试");
        // 第 1 条已生效且未被回滚
        verify(adminService).approve(ROLE, ADMIN, 1L);
    }

    @Test
    void reject_requires_reason() {
        when(itemMapper.selectById(1L)).thenReturn(item(1L, "pending", 0));
        assertThrows(BizException.class,
                () -> service.auditItems(ROLE, ADMIN, List.of(1L), false, "  "));
        assertThrows(BizException.class,
                () -> service.auditItems(ROLE, ADMIN, List.of(1L), false, null));
    }

    @Test
    void empty_and_oversized_batches_rejected() {
        assertThrows(BizException.class, () -> service.auditItems(ROLE, ADMIN, List.of(), true, null));
        assertThrows(BizException.class, () -> service.auditItems(ROLE, ADMIN, null, true, null));
        java.util.List<Long> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < BatchPolicy.MAX_BATCH_SIZE + 1; i++) {
            tooMany.add((long) i);
        }
        assertThrows(BizException.class, () -> service.auditItems(ROLE, ADMIN, tooMany, true, null));
    }

    @Test
    void ban_requires_confirmation() {
        BizException ex = assertThrows(BizException.class,
                () -> service.banUsers(ROLE, ADMIN, List.of(1L), 1, false));
        assertEquals(Code.BATCH_CONFIRM_REQUIRED.getCode(), ex.getCode());

        BizException ex2 = assertThrows(BizException.class,
                () -> service.banUsers(ROLE, ADMIN, List.of(1L), 1, null));
        assertEquals(Code.BATCH_CONFIRM_REQUIRED.getCode(), ex2.getCode());

        // 未确认时绝不执行任何写操作
        verify(adminService, never()).banUser(any(), anyLong(), anyLong(), any());
    }

    @Test
    void ban_success_when_confirmed() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 0));
        BatchResultVO vo = service.banUsers(ROLE, ADMIN, List.of(1L), 1, true);
        assertEquals(1, vo.getSuccess());
        verify(adminService).banUser(ROLE, ADMIN, 1L, 1);
        verify(auditLogMapper).insert(any(AuditLog.class));
    }

    @Test
    void ban_skips_same_state_to_avoid_double_credit_recompute() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 1));
        BatchResultVO vo = service.banUsers(ROLE, ADMIN, List.of(1L), 1, true);
        assertEquals(1, vo.getSkipped());
        assertTrue(vo.getItems().get(0).getReason().contains("已是目标状态"));
        verify(adminService, never()).banUser(any(), anyLong(), anyLong(), any());
    }

    @Test
    void ban_rejects_illegal_status() {
        assertThrows(BizException.class, () -> service.banUsers(ROLE, ADMIN, List.of(1L), 9, true));
        assertThrows(BizException.class, () -> service.banUsers(ROLE, ADMIN, List.of(1L), null, true));
    }

    @Test
    void audit_failure_still_returns_result() {
        when(itemMapper.selectById(1L)).thenReturn(item(1L, "pending", 0));
        doThrow(new RuntimeException("db down")).when(auditLogMapper).insert(any(AuditLog.class));

        BatchResultVO vo = service.auditItems(ROLE, ADMIN, List.of(1L), true, null);
        assertEquals(1, vo.getSuccess(), "审计失败不得阻断批量结果返回");
        assertEquals(null, vo.getAuditLogId());
    }

    @Test
    void audit_exception_on_single_item_isolated() {
        when(itemMapper.selectById(1L)).thenReturn(item(1L, "pending", 0));
        when(itemMapper.selectById(2L)).thenReturn(item(2L, "pending", 0));
        doThrow(new RuntimeException("boom")).when(adminService).approve(ROLE, ADMIN, 1L);

        BatchResultVO vo = service.auditItems(ROLE, ADMIN, List.of(1L, 2L), true, null);
        assertEquals(1, vo.getFailed());
        assertEquals(1, vo.getSuccess());
        assertNotNull(vo.getItems().get(0).getReason());
    }

    @Test
    void preview_counts_actionable_items() {
        when(itemMapper.selectById(1L)).thenReturn(item(1L, "pending", 0));
        when(itemMapper.selectById(2L)).thenReturn(item(2L, "pass", 0));
        when(itemMapper.selectById(3L)).thenReturn(item(3L, "reject", 0));
        when(itemMapper.selectById(4L)).thenReturn(null);

        BatchPreviewVO p = service.previewItems(List.of(1L, 2L, 3L, 4L));
        assertEquals(4, p.getSelected());
        assertEquals(3, p.getExisting());
        assertEquals(1, p.getActionable());
        assertEquals(1, p.getAlreadyPass());
        assertEquals(1, p.getAlreadyReject());
        assertEquals(3, p.skipped());
    }
}
