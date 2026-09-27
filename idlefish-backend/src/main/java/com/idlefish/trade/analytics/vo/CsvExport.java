package com.idlefish.trade.analytics.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CSV 导出载体（F-15.5）。content 为纯文本（含 UTF-8 BOM 由 Controller 注入）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CsvExport {

    /** 建议下载文件名，如 analytics-gmv-20260927.csv。 */
    private String filename;
    /** CSV 文本内容。 */
    private String content;
}
