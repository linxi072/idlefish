package com.idlefish.trade.admin.batch.controller;

import com.idlefish.trade.admin.batch.AdminBatchService;
import com.idlefish.trade.admin.batch.BatchPreviewVO;
import com.idlefish.trade.admin.batch.BatchResultVO;
import com.idlefish.trade.admin.batch.dto.BatchAuditDTO;
import com.idlefish.trade.admin.batch.dto.BatchBanDTO;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台批量操作（ITER-U03）：商品审核 / 用户封禁，以及执行前的影响面预览。
 *
 * <p>所有写操作经 RBAC 权限校验与批次审计留痕（由 {@link AdminBatchService} 内部完成）；
 * 逐条复用单条操作逻辑，保证与单条处理的规则、通知、审计完全一致。
 *
 * <p>结果按「成功 / 跳过 / 失败」三类逐条返回（REQ-02/REQ-04），部分失败不回滚，
 * 失败 ID 可直接用于重试（REQ-05）。
 */
@RestController
@RequestMapping("/api/admin/batch")
public class AdminBatchController {

    private final AdminBatchService batchService;

    public AdminBatchController(AdminBatchService batchService) {
        this.batchService = batchService;
    }

    /** 批量商品审核（通过 / 驳回），返回逐条结果。 */
    @PostMapping("/items/audit")
    public Result<BatchResultVO> auditItems(@CurrentAdmin AdminUser admin, @Valid @RequestBody BatchAuditDTO dto) {
        return Result.ok(batchService.auditItems(admin.getRole(), admin.getId(),
                dto.getIds(), Boolean.TRUE.equals(dto.getPass()), dto.getReason()));
    }

    /** 批量操作影响面预览（REQ-03：二次确认弹窗的数据来源）。 */
    @PostMapping("/items/preview")
    public Result<BatchPreviewVO> previewItems(@CurrentAdmin AdminUser admin, @Valid @RequestBody BatchAuditDTO dto) {
        return Result.ok(batchService.previewItems(dto.getIds()));
    }

    /** 批量封禁 / 解封（高风险，须 confirmed=true）。 */
    @PostMapping("/users/ban")
    public Result<BatchResultVO> banUsers(@CurrentAdmin AdminUser admin, @Valid @RequestBody BatchBanDTO dto) {
        return Result.ok(batchService.banUsers(admin.getRole(), admin.getId(),
                dto.getIds(), dto.getStatus(), dto.getConfirmed()));
    }
}
