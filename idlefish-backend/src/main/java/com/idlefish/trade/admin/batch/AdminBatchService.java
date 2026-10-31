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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台批量操作服务（ITER-U03 REQ-01/02/03/04/05/11）。
 *
 * <p>设计约定：
 * <ul>
 *   <li><b>复用单条逻辑</b>：逐条委托 {@link AdminService} 的审核/封禁，保证权限校验、状态机校验、
 *       通知触达与逐条审计与单条操作完全一致，不另起一套规则（避免两套语义漂移）。</li>
 *   <li><b>部分失败不回滚</b>（REQ-05）：单条失败仅记录原因，已成功项保持生效；失败 ID 原样返回供重试。</li>
 *   <li><b>预检跳过</b>（REQ-02）：不存在 / 已删除 / 已是目标态一律跳过并给出明确原因，
 *       跳过项不产生副作用（尤其避免重复通知卖家、重复触发信用重算）。</li>
 *   <li><b>审计留痕</b>（REQ-11）：批次维度追加一条审计日志，记录操作人/条数/条件/结果摘要。</li>
 * </ul>
 */
@Service
public class AdminBatchService {

    private static final Logger log = LoggerFactory.getLogger(AdminBatchService.class);

    private final AdminService adminService;
    private final AdminAuthService adminAuthService;
    private final ItemMapper itemMapper;
    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;
    private final MetricsRegistry metrics;

    public AdminBatchService(AdminService adminService, AdminAuthService adminAuthService,
                             ItemMapper itemMapper, UserMapper userMapper,
                             AuditLogMapper auditLogMapper, MetricsRegistry metrics) {
        this.adminService = adminService;
        this.adminAuthService = adminAuthService;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
        this.auditLogMapper = auditLogMapper;
        this.metrics = metrics;
    }

    /**
     * 批量商品审核（通过 / 驳回）。
     *
     * @param pass   true 通过；false 驳回（驳回时 reason 必填）
     * @param reason 驳回原因（通过时忽略）
     */
    public BatchResultVO auditItems(String role, Long operatorId, List<Long> ids, boolean pass, String reason) {
        requireIds(ids);
        // 权限在最外层一次性校验：无权限时整体拒绝，而非让每条都失败（避免噪声结果）
        adminAuthService.require(role, BatchPolicy.Action.ITEM_AUDIT.getPermission());
        if (!pass && (reason == null || reason.isBlank())) {
            throw new BizException(Code.PARAM_INVALID, "批量驳回须填写驳回原因");
        }

        List<BatchItemResult> results = new ArrayList<>(ids.size());
        for (Long id : ids) {
            results.add(auditOne(role, operatorId, id, pass, reason));
        }
        Long auditId = recordBatch(operatorId, "batch:item:audit", "item",
                "批量审核：" + (pass ? "通过" : "驳回") + (pass ? "" : ("，原因：" + reason.trim())), results);
        metrics.increment("admin.batch.item_audit");
        return build(results, auditId);
    }

    /**
     * 批量封禁 / 解封用户（高风险，须二次确认）。
     *
     * @param status    目标状态：0 正常 / 1 封禁
     * @param confirmed 二次确认标记（REQ-03）
     */
    public BatchResultVO banUsers(String role, Long operatorId, List<Long> ids, Integer status, Boolean confirmed) {
        requireIds(ids);
        String confirmError = BatchPolicy.confirmViolation(BatchPolicy.Action.USER_BAN, confirmed);
        if (confirmError != null) {
            throw new BizException(Code.BATCH_CONFIRM_REQUIRED, confirmError);
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(Code.PARAM_INVALID, "目标状态不合法（0 正常 / 1 封禁）");
        }
        adminAuthService.require(role, BatchPolicy.Action.USER_BAN.getPermission());

        List<BatchItemResult> results = new ArrayList<>(ids.size());
        for (Long id : ids) {
            results.add(banOne(role, operatorId, id, status));
        }
        Long auditId = recordBatch(operatorId, "batch:user:ban", "user",
                "批量" + (status == 1 ? "封禁" : "解封"), results);
        metrics.increment("admin.batch.user_ban");
        return build(results, auditId);
    }

    /** 批量操作前的影响面预览（REQ-03：二次确认前展示影响条数与范围摘要）。 */
    public BatchPreviewVO previewItems(List<Long> ids) {
        requireIds(ids);
        int existing = 0;
        int alreadyPass = 0;
        int alreadyReject = 0;
        int pending = 0;
        for (Long id : ids) {
            Item item = itemMapper.selectById(id);
            if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
                continue;
            }
            existing++;
            if ("pass".equals(item.getAuditStatus())) {
                alreadyPass++;
            } else if ("reject".equals(item.getAuditStatus())) {
                alreadyReject++;
            } else {
                pending++;
            }
        }
        return BatchPreviewVO.of(ids.size(), existing, pending, alreadyPass, alreadyReject);
    }

    // ===== 内部实现 =====

    private BatchItemResult auditOne(String role, Long operatorId, Long id, boolean pass, String reason) {
        String targetId = String.valueOf(id);
        Item item;
        try {
            item = itemMapper.selectById(id);
        } catch (Exception e) {
            log.warn("[admin.batch] 商品读取异常，按失败处理 itemId={}", id, e);
            return BatchItemResult.failed(targetId, "商品读取失败：" + e.getMessage());
        }
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            return BatchItemResult.skipped(targetId, "商品不存在或已删除");
        }
        if (pass && "pass".equals(item.getAuditStatus())) {
            return BatchItemResult.skipped(targetId, "已是通过状态，跳过以避免重复通知卖家");
        }
        if (!pass && "reject".equals(item.getAuditStatus())) {
            return BatchItemResult.skipped(targetId, "已是驳回状态，跳过以避免重复通知卖家");
        }
        try {
            if (pass) {
                adminService.approve(role, operatorId, id);
                return BatchItemResult.success(targetId, "审核通过");
            }
            adminService.reject(role, operatorId, id, reason.trim());
            return BatchItemResult.success(targetId, "审核驳回");
        } catch (BizException e) {
            return BatchItemResult.failed(targetId, e.getMessage());
        } catch (Exception e) {
            log.warn("[admin.batch] 商品审核异常 itemId={}", id, e);
            return BatchItemResult.failed(targetId, "处理异常：" + e.getMessage());
        }
    }

    private BatchItemResult banOne(String role, Long operatorId, Long id, Integer status) {
        String targetId = String.valueOf(id);
        User user;
        try {
            user = userMapper.selectById(id);
        } catch (Exception e) {
            log.warn("[admin.batch] 用户读取异常，按失败处理 userId={}", id, e);
            return BatchItemResult.failed(targetId, "用户读取失败：" + e.getMessage());
        }
        if (user == null) {
            return BatchItemResult.skipped(targetId, "用户不存在");
        }
        if (status.equals(user.getStatus())) {
            return BatchItemResult.skipped(targetId, "已是目标状态，跳过以避免重复触发信用重算");
        }
        try {
            adminService.banUser(role, operatorId, id, status);
            return BatchItemResult.success(targetId, status == 1 ? "已封禁" : "已解封");
        } catch (BizException e) {
            return BatchItemResult.failed(targetId, e.getMessage());
        } catch (Exception e) {
            log.warn("[admin.batch] 用户封禁异常 userId={}", id, e);
            return BatchItemResult.failed(targetId, "处理异常：" + e.getMessage());
        }
    }

    private void requireIds(List<Long> ids) {
        int size = ids == null ? 0 : ids.size();
        String violation = BatchPolicy.sizeViolation(size);
        if (violation != null) {
            throw new BizException(Code.PARAM_INVALID, violation);
        }
        for (Long id : ids) {
            if (id == null) {
                throw new BizException(Code.PARAM_INVALID, "批量目标中存在空 ID");
            }
        }
    }

    /** 批次维度审计留痕（REQ-11）：操作人 / 条数 / 条件 / 结果摘要。 */
    private Long recordBatch(Long operatorId, String action, String targetType, String condition,
                             List<BatchItemResult> results) {
        int success = 0;
        int skipped = 0;
        int failed = 0;
        List<String> failedIds = new ArrayList<>();
        for (BatchItemResult r : results) {
            if (BatchItemResult.SUCCESS.equals(r.getOutcome())) {
                success++;
            } else if (BatchItemResult.SKIPPED.equals(r.getOutcome())) {
                skipped++;
            } else {
                failed++;
                failedIds.add(r.getTargetId());
            }
        }
        String detail = condition + "，total=" + results.size() + "，success=" + success
                + "，skipped=" + skipped + "，failed=" + failed
                + (failedIds.isEmpty() ? "" : ("，failedIds=" + String.join(",", failedIds)));
        try {
            AuditLog al = new AuditLog();
            al.setOperatorId(operatorId);
            al.setAction(action);
            al.setTargetType(targetType);
            al.setTargetId("batch:" + results.size());
            al.setDetail(detail.length() > 1000 ? detail.substring(0, 1000) : detail);
            auditLogMapper.insert(al);
            return al.getId();
        } catch (Exception e) {
            // 审计失败不阻断批量结果返回，但必须留日志告警
            log.warn("[admin.batch] 批次审计留痕失败 operatorId={} action={}", operatorId, action, e);
            return null;
        }
    }

    private BatchResultVO build(List<BatchItemResult> results, Long auditLogId) {
        List<String> failedIds = new ArrayList<>();
        for (BatchItemResult r : results) {
            if (BatchItemResult.FAILED.equals(r.getOutcome())) {
                failedIds.add(r.getTargetId());
            }
        }
        return BatchResultVO.of(results, failedIds, auditLogId);
    }
}
