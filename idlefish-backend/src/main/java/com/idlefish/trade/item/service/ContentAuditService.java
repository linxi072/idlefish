package com.idlefish.trade.item.service;

import com.idlefish.trade.item.dto.AuditResult;

import java.util.List;

/**
 * 内容审核抽象（PRD §B3 AI 机审 / §E3 IM 审核）：
 * Mock 实现本地关键词/正则机审（复用 SensitiveWords）；
 * 真实实现对接阿里云内容安全（绿网）文本/图片检测。
 * 由 idlefish.audit.mock 开关切换（默认 Mock，沙箱可跑通）。
 */
public interface ContentAuditService {

    /** 审核商品标题/描述/图片（图片为可访问 URL 列表）。 */
    AuditResult audit(String title, String description, List<String> images);

    /** 审核单段文本（IM 消息等）。 */
    default AuditResult auditText(String text) {
        return audit(text, null, null);
    }
}
