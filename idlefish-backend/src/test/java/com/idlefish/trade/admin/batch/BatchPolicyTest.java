package com.idlefish.trade.admin.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 批量操作策略纯函数单测（ITER-U03 REQ-03/REQ-05）。 */
class BatchPolicyTest {

    @Test
    void size_boundary() {
        assertNotNull(BatchPolicy.sizeViolation(0), "空列表必须拒绝");
        assertNotNull(BatchPolicy.sizeViolation(-1));
        assertNull(BatchPolicy.sizeViolation(1));
        assertNull(BatchPolicy.sizeViolation(BatchPolicy.MAX_BATCH_SIZE), "等于上限放行");
        assertNotNull(BatchPolicy.sizeViolation(BatchPolicy.MAX_BATCH_SIZE + 1), "超上限必须拒绝");
        assertTrue(BatchPolicy.sizeViolation(BatchPolicy.MAX_BATCH_SIZE + 1).contains("上限"));
    }

    @Test
    void high_risk_actions_require_confirm() {
        assertTrue(BatchPolicy.requiresConfirm(BatchPolicy.Action.USER_BAN));
        assertFalse(BatchPolicy.requiresConfirm(BatchPolicy.Action.ITEM_AUDIT));
        assertFalse(BatchPolicy.requiresConfirm(null));

        assertNotNull(BatchPolicy.confirmViolation(BatchPolicy.Action.USER_BAN, false));
        assertNotNull(BatchPolicy.confirmViolation(BatchPolicy.Action.USER_BAN, null));
        assertNull(BatchPolicy.confirmViolation(BatchPolicy.Action.USER_BAN, true));
        // 非高风险动作不强制确认
        assertNull(BatchPolicy.confirmViolation(BatchPolicy.Action.ITEM_AUDIT, null));
    }

    @Test
    void actions_declare_permission() {
        assertEquals("item:audit", BatchPolicy.Action.ITEM_AUDIT.getPermission());
        assertEquals("user:ban", BatchPolicy.Action.USER_BAN.getPermission());
    }

    @Test
    void result_counters_and_retry_ids() {
        BatchResultVO vo = BatchResultVO.of(java.util.List.of(
                BatchItemResult.success("1", "审核通过"),
                BatchItemResult.skipped("2", "已是目标状态"),
                BatchItemResult.failed("3", "状态不允许")),
                java.util.List.of("3"), 99L);
        assertEquals(3, vo.getTotal());
        assertEquals(1, vo.getSuccess());
        assertEquals(1, vo.getSkipped());
        assertEquals(1, vo.getFailed());
        assertEquals(java.util.List.of("3"), vo.getFailedIds(), "失败 ID 必须可用于重试");
        assertEquals(Long.valueOf(99L), vo.getAuditLogId());
        assertFalse(vo.allSucceeded());

        BatchResultVO all = BatchResultVO.of(java.util.List.of(BatchItemResult.success("1", "ok")),
                java.util.List.of(), 1L);
        assertTrue(all.allSucceeded());
    }

    @Test
    void preview_skipped_derivation() {
        BatchPreviewVO p = BatchPreviewVO.of(10, 8, 5, 2, 1);
        assertEquals(10, p.getSelected());
        assertEquals(8, p.getExisting());
        assertEquals(5, p.getActionable());
        assertEquals(5, p.skipped(), "跳过数 = 勾选数 - 可操作数");
    }
}
