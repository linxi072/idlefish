package com.idlefish.trade.export;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.datascope.DataScopeDecision;
import com.idlefish.trade.datascope.service.DataScopeService;
import com.idlefish.trade.risk.entity.AuditLog;
import com.idlefish.trade.risk.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导出编排服务（ITER-T06 运营数据导出中心 / REQ-07）。
 * <p>
 * 安全优先：导出范围由 {@link DataScopeService} 决议驱动，复用 ITER-T05 数据权限基础设施：
 * <ul>
 *   <li>带 {@code org_id} 的实体（管理员 / 商品）：仅导出当前管理员<b>可见机构</b>数据，越权机构请求被拒绝并记审计；</li>
 *   <li>无 {@code org_id} 的实体（用户 / 订单）：仅「不受限（ALL）」管理员可导出，杜绝跨机构数据泄露；</li>
 *   <li>下载二次鉴权：仅当下载者可见范围覆盖任务快照机构时才放行。</li>
 * </ul>
 * 每次导出落 {@code t_export_task} 一条审计记录，敏感字段（手机号）脱敏，CSV 经字段转义防注入。
 */
@Service
public class ExportService {

    /** 越权/导出审计动作。 */
    static final String ACTION_DENY = "EXPORT_DATA_SCOPE_DENY";
    static final String ACTION_CREATE = "EXPORT_CREATE";

    private final Map<ExportType, ExportProvider> providers;
    private final DataScopeService dataScopeService;
    private final AuditLogMapper auditLogMapper;
    private final ExportTaskMapper exportTaskMapper;
    private final ExportFileStore fileStore;
    private final IdlefishProperties props;

    public ExportService(List<ExportProvider> providerList,
                         DataScopeService dataScopeService,
                         AuditLogMapper auditLogMapper,
                         ExportTaskMapper exportTaskMapper,
                         ExportFileStore fileStore,
                         IdlefishProperties props) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(ExportProvider::type, p -> p, (a, b) -> a));
        this.dataScopeService = dataScopeService;
        this.auditLogMapper = auditLogMapper;
        this.exportTaskMapper = exportTaskMapper;
        this.fileStore = fileStore;
        this.props = props;
    }

    /** 枚举当前可导出类型（含范围约束提示）。 */
    public List<ExportTypeInfo> listTypes() {
        List<ExportTypeInfo> list = new ArrayList<>();
        for (ExportType t : ExportType.values()) {
            list.add(new ExportTypeInfo(t.getCode(), t.getLabel(), t.isHasOrgScope()));
        }
        return list;
    }

    /** 预览：校验范围并取数，返回表头与预计行数（不落盘）。 */
    public ExportPreview preview(long adminId, ExportQuery query) {
        checkFormat(query);
        ExportProvider provider = resolveProvider(query.getType());
        ExportContext ctx = resolveContext(adminId, provider, query);
        List<List<String>> rows = provider.rows(ctx);
        return new ExportPreview(provider.headers(), rows.size(),
                ctx.isUnrestricted() ? null : new ArrayList<>(ctx.getOrgIds()));
    }

    /** 导出：校验范围 → 取数 → 构造 CSV → 落盘 → 记任务 → 返回任务。 */
    public ExportTask export(long adminId, ExportQuery query) {
        checkFormat(query);
        ExportProvider provider = resolveProvider(query.getType());
        ExportContext ctx = resolveContext(adminId, provider, query);

        List<List<String>> rows = provider.rows(ctx);
        String csv = ExportCsvBuilder.build(provider.headers(), rows);
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);

        long maxBytes = props.getExport().getMaxFileMb() * 1024L * 1024L;
        if (content.length > maxBytes) {
            throw new BizException(Code.EXPORT_LIMIT_EXCEEDED, "导出文件超过上限（" + props.getExport().getMaxFileMb() + "MB）");
        }

        String exportNo = IdGenerator.exportNo();
        String fileKey = exportNo + ".csv";
        try {
            fileStore.write(fileKey, content);
        } catch (IOException e) {
            throw new BizException(Code.SYSTEM_ERROR, "写入导出文件失败");
        }

        ExportTask task = new ExportTask();
        task.setExportNo(exportNo);
        task.setType(provider.type().getCode());
        task.setAdminId(adminId);
        task.setOrgIdsSnapshot(snapshot(ctx.getOrgIds()));
        task.setRowCount(rows.size());
        task.setFormat(query.getFormat());
        task.setFileKey(fileKey);
        task.setFileName(provider.type().getLabel() + "_" + exportNo + ".csv");
        task.setStatus("DONE");
        exportTaskMapper.insert(task);

        writeAudit(adminId, ACTION_CREATE,
                "type=" + task.getType() + ", exportNo=" + exportNo + ", rows=" + rows.size()
                        + ", orgIds=" + (task.getOrgIdsSnapshot() == null ? "ALL" : task.getOrgIdsSnapshot()));
        return task;
    }

    /** 下载：加载任务 → 二次范围鉴权 → 读取文件。 */
    public ExportFile download(long adminId, String exportNo) {
        if (exportNo == null || exportNo.isBlank()) {
            throw new BizException(Code.EXPORT_TASK_NOT_FOUND);
        }
        ExportTask task = exportTaskMapper.selectByExportNo(exportNo);
        if (task == null) {
            throw new BizException(Code.EXPORT_TASK_NOT_FOUND);
        }
        authorizeDownload(adminId, task);
        byte[] content;
        try {
            content = fileStore.read(task.getFileKey());
        } catch (FileNotFoundException e) {
            throw new BizException(Code.EXPORT_FILE_NOT_FOUND);
        } catch (IOException e) {
            throw new BizException(Code.SYSTEM_ERROR, "读取导出文件失败");
        }
        return new ExportFile(task.getFileName(), content, "text/csv;charset=utf-8");
    }

    // ===== 范围约束 =====

    private ExportContext resolveContext(long adminId, ExportProvider provider, ExportQuery query) {
        DataScopeDecision decision = dataScopeService.resolveForAdmin(adminId);
        int limit = resolveLimit(query);

        if (!provider.hasOrgScope()) {
            // 无 org_id 实体：仅不受限（ALL）管理员可导出（REQ-07）
            if (!decision.isUnrestricted()) {
                writeDenyAudit(adminId, provider.type().getCode(), null);
                throw new BizException(Code.EXPORT_DATA_SCOPE_FORBIDDEN);
            }
            return new ExportContext(limit, null);
        }

        Set<Long> allowed = decision.getOrgIds(); // null = 不受限
        List<Long> requested = query.getOrgIds();
        if (requested != null && !requested.isEmpty()) {
            Set<Long> req = new LinkedHashSet<>(requested);
            Set<Long> bad = new LinkedHashSet<>();
            if (allowed != null) {
                for (Long o : req) {
                    if (!allowed.contains(o)) {
                        bad.add(o);
                    }
                }
            }
            if (!bad.isEmpty()) {
                writeDenyAudit(adminId, provider.type().getCode(), bad);
                throw new BizException(Code.EXPORT_DATA_SCOPE_FORBIDDEN);
            }
            return new ExportContext(limit, req);
        }
        return new ExportContext(limit, allowed);
    }

    private void authorizeDownload(long adminId, ExportTask task) {
        DataScopeDecision decision = dataScopeService.resolveForAdmin(adminId);
        Set<Long> taskOrgIds = parseSnapshot(task.getOrgIdsSnapshot());
        if (taskOrgIds == null) {
            if (!decision.isUnrestricted()) {
                writeDenyAudit(adminId, task.getType(), null);
                throw new BizException(Code.EXPORT_DATA_SCOPE_FORBIDDEN);
            }
            return;
        }
        Set<Long> allowed = decision.getOrgIds();
        if (allowed != null && !allowed.containsAll(taskOrgIds)) {
            writeDenyAudit(adminId, task.getType(), taskOrgIds);
            throw new BizException(Code.EXPORT_DATA_SCOPE_FORBIDDEN);
        }
    }

    private int resolveLimit(ExportQuery query) {
        int max = props.getExport().getMaxRowsPerExport();
        int limit = (query.getLimit() != null && query.getLimit() > 0) ? query.getLimit() : max;
        if (limit > max) {
            throw new BizException(Code.EXPORT_LIMIT_EXCEEDED);
        }
        return limit;
    }

    private void checkFormat(ExportQuery query) {
        if (!"CSV".equalsIgnoreCase(query.getFormat())) {
            throw new BizException(Code.EXPORT_TYPE_UNSUPPORTED, "仅支持 CSV 格式");
        }
    }

    private ExportProvider resolveProvider(String type) {
        ExportType t = ExportType.fromCode(type);
        ExportProvider p = providers.get(t);
        if (p == null) {
            throw new BizException(Code.EXPORT_TYPE_UNSUPPORTED);
        }
        return p;
    }

    // ===== 审计辅助 =====

    private void writeDenyAudit(long adminId, String type, Set<Long> deniedOrgIds) {
        writeAudit(adminId, ACTION_DENY,
                "type=" + type + (deniedOrgIds == null ? "" : ", deniedOrgIds=" + deniedOrgIds));
    }

    private void writeAudit(long adminId, String action, String detail) {
        try {
            AuditLog log = new AuditLog();
            log.setOperatorId(adminId);
            log.setAction(action);
            log.setTargetType("export");
            log.setTargetId(String.valueOf(adminId));
            log.setDetail(detail);
            auditLogMapper.insert(log);
        } catch (Exception ignore) {
            // 审计写失败不应阻断主流程
        }
    }

    // ===== 快照序列化（依赖无关，避免引入 JSON 库） =====

    static String snapshot(Set<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return null;
        }
        return "[" + orgIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    static Set<Long> parseSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank() || "[]".equals(snapshot.trim())) {
            return null;
        }
        String inner = snapshot.trim();
        inner = inner.substring(1, inner.length() - 1);
        Set<Long> set = new LinkedHashSet<>();
        for (String s : inner.split(",")) {
            if (!s.isBlank()) {
                set.add(Long.parseLong(s.trim()));
            }
        }
        return set.isEmpty() ? null : set;
    }
}
