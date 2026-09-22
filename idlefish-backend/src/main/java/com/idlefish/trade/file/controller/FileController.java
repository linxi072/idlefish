package com.idlefish.trade.file.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.file.service.FileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口（需登录）。返回可访问 URL，供发布商品携带图片。
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /** 上传单个文件，返回可访问 URL。 */
    @PostMapping("/upload")
    public Result<String> upload(@CurrentUser LoginUser user, @RequestParam("file") MultipartFile file) {
        return Result.ok(fileService.store(user.getUserId(), file));
    }
}
