package com.idlefish.trade.export;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 运营数据导出中心后台（ITER-T06 / REQ-07）。
 * 鉴权沿用 RBAC 拦截器（{@code AdminAuthInterceptor}），本控制器聚焦导出自身。
 * 四个端点均 auth=admin，导出范围由 {@link ExportService} 结合 ITER-T05 数据权限裁决。
 */
@RestController
@RequestMapping("/api/admin/export")
public class AdminExportController {

    private final ExportService exportService;

    public AdminExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /** 枚举可导出类型及其范围约束提示。 */
    @GetMapping("/types")
    public Result<java.util.List<ExportTypeInfo>> types(@CurrentAdmin AdminUser admin) {
        return Result.ok(exportService.listTypes());
    }

    /** 导出预览：校验范围并取数，返回表头与预计行数（不落盘）。 */
    @PostMapping("/preview")
    public Result<ExportPreview> preview(@CurrentAdmin AdminUser admin, @Valid @RequestBody ExportQuery query) {
        return Result.ok(exportService.preview(admin.getId(), query));
    }

    /** 执行导出：校验范围 → 取数 → 落盘 → 记任务。 */
    @PostMapping("/export")
    public Result<ExportTask> export(@CurrentAdmin AdminUser admin, @Valid @RequestBody ExportQuery query) {
        return Result.ok(exportService.export(admin.getId(), query));
    }

    /** 下载导出文件（二次范围鉴权）。 */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@CurrentAdmin AdminUser admin, @RequestParam String exportNo) {
        ExportFile file = exportService.download(admin.getId(), exportNo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.getContentType()));
        headers.setContentDispositionFormData("attachment", file.getFileName());
        headers.setContentLength(file.getContent().length);
        return ResponseEntity.ok()
                .headers(headers)
                .body(file.getContent());
    }
}
