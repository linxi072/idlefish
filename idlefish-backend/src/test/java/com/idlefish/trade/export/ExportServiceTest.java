package com.idlefish.trade.export;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.datascope.DataScope;
import com.idlefish.trade.datascope.DataScopeDecision;
import com.idlefish.trade.risk.entity.AuditLog;
import com.idlefish.trade.risk.mapper.AuditLogMapper;
import com.idlefish.trade.datascope.service.DataScopeService;
import com.idlefish.trade.export.ExportProvider;
import com.idlefish.trade.export.ExportTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExportService 编排单测（ITER-T06 / REQ-07）：范围裁决、越权审计、落盘、下载鉴权、快照序列化。
 * 纯 Mockito，离线可跑，不依赖 Spring / MySQL。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportServiceTest {

    @Mock private DataScopeService dataScopeService;
    @Mock private AuditLogMapper auditLogMapper;
    @Mock private ExportTaskMapper exportTaskMapper;
    @Mock private ExportFileStore fileStore;

    private ExportProvider userProvider;
    private ExportProvider orderProvider;
    private ExportProvider adminUserProvider;
    private ExportProvider itemProvider;

    private IdlefishProperties props;
    private ExportService service;

    @BeforeEach
    void setUp() {
        props = new IdlefishProperties();
        userProvider = mockProvider(ExportType.USER, false,
                List.of("id", "phone"),
                List.of(List.of("1", "13800001111"), List.of("2", "13900002222")));
        orderProvider = mockProvider(ExportType.ORDER, false,
                List.of("orderNo"), List.of(List.of("O1")));
        adminUserProvider = mockProvider(ExportType.ADMIN_USER, true,
                List.of("id", "orgId"), List.of(List.of("1", "1")));
        itemProvider = mockProvider(ExportType.ITEM, true,
                List.of("id", "orgId"), List.of(List.of("1", "1"), List.of("2", "2")));
        List<ExportProvider> providers = List.of(userProvider, orderProvider, adminUserProvider, itemProvider);
        service = new ExportService(providers, dataScopeService, auditLogMapper, exportTaskMapper, fileStore, props);
    }

    private ExportProvider mockProvider(ExportType type, boolean hasOrgScope,
                                        List<String> headers, List<List<String>> rows) {
        ExportProvider p = org.mockito.Mockito.mock(ExportProvider.class);
        when(p.type()).thenReturn(type);
        when(p.hasOrgScope()).thenReturn(hasOrgScope);
        when(p.headers()).thenReturn(headers);
        when(p.rows(any())).thenReturn(rows);
        return p;
    }

    private DataScopeDecision restricted(Set<Long> orgIds) {
        return new DataScopeDecision(1L, orgIds.iterator().next(), DataScope.APPOINT, orgIds);
    }

    private DataScopeDecision unrestricted() {
        return DataScopeDecision.unrestricted(1L, 1L);
    }

    private ExportQuery query(String type, List<Long> orgIds, Integer limit, String format) {
        ExportQuery q = new ExportQuery();
        q.setType(type);
        q.setOrgIds(orgIds);
        q.setLimit(limit);
        q.setFormat(format);
        return q;
    }

    @Test
    void listTypes_fourEntriesWithScopeHints() {
        assertEquals(4, service.listTypes().size());
        assertTrue(service.listTypes().stream().anyMatch(t -> "item".equals(t.getCode()) && t.isHasOrgScope()));
        assertTrue(service.listTypes().stream().anyMatch(t -> "user".equals(t.getCode()) && !t.isHasOrgScope()));
    }

    @Test
    void preview_orgScope_withAllowedOrgIds() {
        when(dataScopeService.resolveForAdmin(1L)).thenReturn(restricted(Set.of(1L, 2L, 3L)));
        ExportPreview p = service.preview(1L, query("item", List.of(2L), null, "CSV"));
        assertEquals(2, p.getRowCount());
        assertEquals(List.of("id", "orgId"), p.getHeaders());
        assertEquals(List.of(2L), p.getOrgIds());
    }

    @Test
    void preview_noOrgScope_nonAllAdmin_forbidden() {
        when(dataScopeService.resolveForAdmin(1L)).thenReturn(restricted(Set.of(1L)));
        BizException ex = assertThrows(BizException.class,
                () -> service.preview(1L, query("user", null, null, "CSV")));
        assertEquals(Code.EXPORT_DATA_SCOPE_FORBIDDEN.getCode(), ex.getCode());
        verify(auditLogMapper).insert(any(AuditLog.class));
    }

    @Test
    void export_success_writesFileAndTask() throws IOException {
        when(dataScopeService.resolveForAdmin(1L)).thenReturn(unrestricted());
        when(fileStore.write(anyString(), any(byte[].class))).thenReturn("EXP.csv");
        ExportTask t = service.export(1L, query("user", null, null, "CSV"));
        assertNotNull(t.getExportNo());
        assertTrue(t.getExportNo().startsWith("EXP"));
        assertEquals("DONE", t.getStatus());
        assertEquals(2, t.getRowCount());
        assertEquals("user", t.getType());
        verify(fileStore).write(anyString(), any(byte[].class));
        verify(exportTaskMapper).insert(any(ExportTask.class));
        verify(auditLogMapper).insert(any(AuditLog.class));
    }

    @Test
    void export_fileTooLarge_rejected() {
        IdlefishProperties small = new IdlefishProperties();
        small.getExport().setMaxFileMb(0L);
        ExportService svc = new ExportService(List.of(userProvider), dataScopeService,
                auditLogMapper, exportTaskMapper, fileStore, small);
        when(dataScopeService.resolveForAdmin(1L)).thenReturn(unrestricted());
        BizException ex = assertThrows(BizException.class,
                () -> svc.export(1L, query("user", null, null, "CSV")));
        assertEquals(Code.EXPORT_LIMIT_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    void export_unsupportedFormat_rejected() {
        BizException ex = assertThrows(BizException.class,
                () -> service.export(1L, query("user", null, null, "XLSX")));
        assertEquals(Code.EXPORT_TYPE_UNSUPPORTED.getCode(), ex.getCode());
    }

    @Test
    void download_success() throws IOException {
        ExportTask task = new ExportTask();
        task.setExportNo("EXP1");
        task.setType("user");
        task.setOrgIdsSnapshot(null);
        task.setFileKey("EXP1.csv");
        task.setFileName("用户_EXP1.csv");
        task.setStatus("DONE");
        when(exportTaskMapper.selectByExportNo("EXP1")).thenReturn(task);
        when(dataScopeService.resolveForAdmin(1L)).thenReturn(unrestricted());
        when(fileStore.read("EXP1.csv")).thenReturn("csv-bytes".getBytes(StandardCharsets.UTF_8));
        ExportFile f = service.download(1L, "EXP1");
        assertEquals("用户_EXP1.csv", f.getFileName());
        assertEquals("csv-bytes", new String(f.getContent(), StandardCharsets.UTF_8));
    }

    @Test
    void download_taskNotFound() {
        when(exportTaskMapper.selectByExportNo("X")).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.download(1L, "X"));
        assertEquals(Code.EXPORT_TASK_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void download_nonAllDownloadingAllTask_forbidden() {
        ExportTask task = new ExportTask();
        task.setExportNo("EXP1");
        task.setType("user");
        task.setOrgIdsSnapshot(null);
        task.setFileKey("EXP1.csv");
        when(exportTaskMapper.selectByExportNo("EXP1")).thenReturn(task);
        when(dataScopeService.resolveForAdmin(1L)).thenReturn(restricted(Set.of(1L)));
        BizException ex = assertThrows(BizException.class, () -> service.download(1L, "EXP1"));
        assertEquals(Code.EXPORT_DATA_SCOPE_FORBIDDEN.getCode(), ex.getCode());
        verify(auditLogMapper).insert(any(AuditLog.class));
    }

    @Test
    void download_orgMismatch_forbidden() {
        ExportTask task = new ExportTask();
        task.setExportNo("EXP1");
        task.setType("item");
        task.setOrgIdsSnapshot("[1,2]");
        task.setFileKey("EXP1.csv");
        when(exportTaskMapper.selectByExportNo("EXP1")).thenReturn(task);
        when(dataScopeService.resolveForAdmin(1L)).thenReturn(restricted(Set.of(1L)));
        BizException ex = assertThrows(BizException.class, () -> service.download(1L, "EXP1"));
        assertEquals(Code.EXPORT_DATA_SCOPE_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void snapshot_roundtrip() {
        assertNull(ExportService.snapshot(null));
        assertNull(ExportService.snapshot(Set.of()));
        Set<Long> in = new LinkedHashSet<>(List.of(1L, 2L));
        assertEquals("[1,2]", ExportService.snapshot(in));
        assertEquals(new LinkedHashSet<>(List.of(1L, 2L)), ExportService.parseSnapshot("[1,2]"));
        assertNull(ExportService.parseSnapshot(null));
        assertNull(ExportService.parseSnapshot("[]"));
    }
}
