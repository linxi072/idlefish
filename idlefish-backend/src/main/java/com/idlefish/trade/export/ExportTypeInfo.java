package com.idlefish.trade.export;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 可导出类型元信息（供 {@code /types} 端点枚举前端可用类型与范围约束提示）。
 */
@Data
@AllArgsConstructor
public class ExportTypeInfo {
    /** 类型 code（与 ExportType 一致）。 */
    private String code;
    /** 中文标签。 */
    private String label;
    /** 是否带 org_id 列（true 受数据范围约束；false 仅 ALL 管理员可导出）。 */
    private boolean hasOrgScope;
}
