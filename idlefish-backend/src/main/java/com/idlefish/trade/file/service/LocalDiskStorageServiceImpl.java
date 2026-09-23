package com.idlefish.trade.file.service;

import com.idlefish.trade.common.IdlefishProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘对象存储（idlefish.oss.mock=true 默认实现）。
 * 物理落盘到应用工作目录下的 uploads（可经 nginx/Spring 静态映射对外提供），
 * 返回的 URL 前缀取自 idlefish.file.base-url，与现有静态资源配置一致。
 */
@Service
@ConditionalOnProperty(name = "idlefish.oss.mock", havingValue = "true", matchIfMissing = true)
public class LocalDiskStorageServiceImpl implements StorageService {

    private final IdlefishProperties props;
    private final String workDir;

    public LocalDiskStorageServiceImpl(IdlefishProperties props,
                                       @Value("${idlefish.file.upload-dir:uploads}") String uploadDir) {
        this.props = props;
        this.workDir = uploadDir;
    }

    @Override
    public String store(String objectKey, InputStream data, String contentType) {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), workDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(objectKey);
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
            return getAccessUrl(objectKey);
        } catch (IOException e) {
            throw new com.idlefish.trade.common.BizException(com.idlefish.trade.common.Code.FILE_ERROR, "本地文件保存失败");
        }
    }

    @Override
    public String getAccessUrl(String objectKey) {
        String base = props.getFile().getBaseUrl();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return base + objectKey;
    }

    @Override
    public boolean delete(String objectKey) {
        try {
            Path target = Paths.get(System.getProperty("user.dir"), workDir).resolve(objectKey);
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            return false;
        }
    }
}
