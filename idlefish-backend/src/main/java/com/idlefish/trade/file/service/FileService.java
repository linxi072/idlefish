package com.idlefish.trade.file.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 文件存储门面（业务侧统一入口）：
 * - idlefish.file.mock=true：返回伪造 URL（不落盘），用于纯前端联调；
 * - idlefish.file.mock=false：委托 {@link StorageService}：
 *     · idlefish.oss.mock=true（默认）：本地磁盘落盘，演示可用；
 *     · idlefish.oss.mock=false：对接真实阿里云 OSS。
 */
@Service
public class FileService {

    private final IdlefishProperties props;
    private final StorageService storageService;

    public FileService(IdlefishProperties props, StorageService storageService) {
        this.props = props;
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
        IdlefishProperties.File f = props.getFile();
        if (f.isMock()) {
            return f.getBaseUrl() + name;
        }
        try (var in = file.getInputStream()) {
            return storageService.store(name, in, file.getContentType());
        } catch (IOException e) {
            throw new BizException(Code.FILE_ERROR, "文件保存失败");
        }
    }
}
