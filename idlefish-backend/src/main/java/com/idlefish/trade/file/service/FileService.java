package com.idlefish.trade.file.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 文件存储门面（业务侧统一入口）：一律委托真实 {@link StorageService}（阿里云 OSS），
 * 不再提供伪造 URL / 本地落盘的 mock 旁路。
 */
@Service
public class FileService {

    private final StorageService storageService;

    public FileService(StorageService storageService) {
        this.storageService = storageService;
    }

    public String store(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(Code.PARAM_INVALID, "上传文件为空");
        }
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')) : "";
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        try (var in = file.getInputStream()) {
            return storageService.store(name, in, file.getContentType());
        } catch (IOException e) {
            throw new BizException(Code.FILE_ERROR, "文件保存失败");
        }
    }
}
