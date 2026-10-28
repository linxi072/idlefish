package com.idlefish.trade.export;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 导出请求 DTO（ITER-T06）。
 * <ul>
 *   <li>{@code type}：导出类型 code（见 {@link ExportType}），必填（为空由 {@link ExportType#fromCode} 判为不支持）；</li>
 *   <li>{@code orgIds}：范围覆盖（仅 {@code hasOrgScope=true} 的类型有效），须为当前管理员可见机构的子集；</li>
 *   <li>{@code limit}：行数上限（可选，缺省取配置 {@code maxRowsPerExport}）；</li>
 *   <li>{@code format}：导出格式，目前仅支持 CSV。</li>
 * </ul>
 */
@Data
public class ExportQuery {

    /** 导出类型 code（见 ExportType）；为空或非法由 ExportType.fromCode 抛 EXPORT_TYPE_UNSUPPORTED。 */
    @NotBlank(message = "导出类型 code 不能为空")
    private String type;

    /** 范围覆盖（仅带 org_id 的实体有效）；须为可见机构的子集，越权机构将被拒绝并记审计。 */
    private List<Long> orgIds;

    /** 单次导出行数上限（可选）。 */
    private Integer limit;

    /** 导出格式（目前仅 CSV）。 */
    private String format = "CSV";
}
