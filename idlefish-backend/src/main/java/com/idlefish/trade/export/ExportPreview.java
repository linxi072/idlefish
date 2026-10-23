package com.idlefish.trade.export;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 导出预览（不落盘）：返回表头与预计行数，供前端确认范围与体量。
 */
@Data
@AllArgsConstructor
public class ExportPreview {
    /** 表头。 */
    private List<String> headers;
    /** 预计导出行数。 */
    private int rowCount;
    /** 生效的可见机构集合（null 表示不受限全量）。 */
    private List<Long> orgIds;
}
