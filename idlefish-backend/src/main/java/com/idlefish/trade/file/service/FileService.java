package com.idlefish.trade.file.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件存储服务：Mock 模式返回 OSS 风格 URL；真实模式落本地 uploads 目录（生产应替换为 OSS/S3 客户端）。
 */
@Service
public class FileService {

    private final IdlefishProperties props;

    public FileService(IdlefishProperties props) {
        this.props = props;
    }

    public String store(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(Code.PARAM_INVALID, "上传文件为空");
        }
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')) : "";
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        IdlefishProperties.File f = props.getFile();
        if (f.isMock()) {
            return f.getBaseUrl() + name;
        }
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "uploads");
            Files.createDirectories(dir);
            Path target = dir.resolve(name);
            file.transferTo(target.toFile());
            return f.getBaseUrl() + name;
        } catch (Exception e) {
            throw new BizException(Code.FILE_ERROR, "文件保存失败");
        }
    }
}
