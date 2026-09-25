package com.idlefish.trade.file.service;

import java.io.InputStream;

/**
 * 对象存储抽象：屏蔽本地盘与阿里云 OSS 差异。
 * 实现：
 * - {@link OssStorageServiceImpl}（idlefish.oss.mock=false）：对接真实阿里云 OSS（REST + HMAC-SHA1 签名）。
 * 切换由 application.yml 的 idlefish.oss.mock 控制，不改动业务代码。
 */
public interface StorageService {

    /**
     * 存储对象。
     *
     * @param objectKey    对象键（建议全局唯一，如 UUID + 扩展名）
     * @param data         对象输入流（方法内关闭）
     * @param contentType  内容类型，如 image/jpeg
     * @return 可访问的完整 URL
     */
    String store(String objectKey, InputStream data, String contentType);

    /**
     * 由对象键拼接访问 URL（用于已存在对象或回显）。
     */
    String getAccessUrl(String objectKey);

    /**
     * 删除对象（真实 OSS 才真正删除；本地盘亦物理删除）。
     */
    boolean delete(String objectKey);
}
