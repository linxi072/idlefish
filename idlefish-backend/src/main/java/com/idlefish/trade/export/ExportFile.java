package com.idlefish.trade.export;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 导出文件下载载体（供控制器包装为 HTTP 响应）。
 */
@Getter
@AllArgsConstructor
public class ExportFile {
    /** 下载文件名（含扩展名）。 */
    private final String fileName;
    /** 文件字节内容（UTF-8 CSV）。 */
    private final byte[] content;
    /** 内容类型。 */
    private final String contentType;
}
