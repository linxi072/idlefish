package com.idlefish.trade.export;

import java.io.IOException;

/**
 * 导出文件存储抽象（ITER-T06）。与具体落盘介质（本地磁盘 / OSS / 对象存储）解耦，
 * 便于后续替换为云存储而不影响导出编排逻辑。
 */
public interface ExportFileStore {

    /**
     * 写入导出文件。
     *
     * @param fileKey 文件唯一键（由导出单号派生，如 {@code EXP2026...csv}）
     * @param content 文件字节内容（CSV 文本 UTF-8）
     * @return 实际存储键（供后续读取与下载）
     */
    String write(String fileKey, byte[] content) throws IOException;

    /**
     * 读取导出文件内容。
     *
     * @param fileKey 文件键
     * @return 文件字节内容；不存在或被清理时抛 {@link java.io.FileNotFoundException}
     */
    byte[] read(String fileKey) throws IOException;
}
