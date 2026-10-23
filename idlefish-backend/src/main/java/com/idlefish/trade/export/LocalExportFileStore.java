package com.idlefish.trade.export;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地磁盘导出文件存储（ITER-T06 默认实现）。
 * 经 {@code idlefish.export.storage-dir} 配置落盘目录（缺省应用工作目录下 {@code export-files/}）；
 * 生产可切换为 OSS 实现（同 {@link ExportFileStore} 接口），不影响编排层。
 */
@Component
public class LocalExportFileStore implements ExportFileStore {

    private final Path storageDir;

    public LocalExportFileStore(@Value("${idlefish.export.storage-dir:export-files}") String storageDir) {
        this.storageDir = Path.of(storageDir);
    }

    @Override
    public String write(String fileKey, byte[] content) throws IOException {
        Files.createDirectories(storageDir);
        Path target = resolve(fileKey);
        try (FileOutputStream out = new FileOutputStream(target.toFile())) {
            out.write(content);
        }
        return fileKey;
    }

    @Override
    public byte[] read(String fileKey) throws IOException {
        Path target = resolve(fileKey);
        if (!Files.exists(target)) {
            throw new FileNotFoundException("导出文件不存在或已清理: " + fileKey);
        }
        try (FileInputStream in = new FileInputStream(target.toFile())) {
            return in.readAllBytes();
        }
    }

    /** 解析并防御路径穿越：仅允许文件名（不含路径分隔符），杜绝 {@code ../} 越权读盘。 */
    private Path resolve(String fileKey) {
        File f = new File(fileKey);
        if (f.getParent() != null && !f.getParent().isEmpty()) {
            throw new IllegalArgumentException("非法的文件键（禁止路径分隔符）: " + fileKey);
        }
        return storageDir.resolve(fileKey);
    }
}
