package com.idlefish.trade.export;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.IdlefishProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AdminExportController 单测（ITER-T06）：4 端点委托 ExportService 并正确包装响应。
 * 纯 Mockito，离线可跑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminExportControllerTest {

    @Mock private ExportService exportService;
    @InjectMocks private AdminExportController controller;

    private AdminUser admin(long id) {
        AdminUser a = new AdminUser();
        a.setId(id);
        return a;
    }

    @Test
    void types_delegates() {
        when(exportService.listTypes()).thenReturn(List.of(new ExportTypeInfo("user", "用户", false)));
        Result<List<ExportTypeInfo>> r = controller.types(admin(1L));
        assertEquals(0, r.getCode());
        assertEquals(1, r.getData().size());
    }

    @Test
    void preview_delegates() {
        ExportPreview p = new ExportPreview(List.of("id"), 1, null);
        when(exportService.preview(anyLong(), any())).thenReturn(p);
        Result<ExportPreview> r = controller.preview(admin(1L), new ExportQuery());
        assertEquals(0, r.getCode());
        assertEquals(p, r.getData());
    }

    @Test
    void export_delegates() {
        ExportTask t = new ExportTask();
        t.setExportNo("EXP1");
        when(exportService.export(anyLong(), any())).thenReturn(t);
        Result<ExportTask> r = controller.export(admin(1L), new ExportQuery());
        assertEquals(0, r.getCode());
        assertEquals("EXP1", r.getData().getExportNo());
    }

    @Test
    void download_delegatesToServiceAndWrapsAttachment() {
        ExportFile f = new ExportFile("用户_EXP1.csv", "x".getBytes(), "text/csv;charset=utf-8");
        when(exportService.download(anyLong(), anyString())).thenReturn(f);
        ResponseEntity<byte[]> resp = controller.download(admin(1L), "EXP1");
        assertEquals(200, resp.getStatusCodeValue());
        assertArrayEquals("x".getBytes(), resp.getBody());
        assertTrue(resp.getHeaders().getContentDisposition().getFilename().contains("用户_EXP1.csv"));
    }
}
