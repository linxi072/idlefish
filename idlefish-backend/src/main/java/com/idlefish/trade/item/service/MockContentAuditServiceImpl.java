package com.idlefish.trade.item.service;

import com.idlefish.trade.common.util.SensitiveWords;
import com.idlefish.trade.item.dto.AuditResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 本地机审实现（idlefish.audit.mock=true，默认）：
 * 复用 SensitiveWords 做涉诈词 + 站外联系方式检测，命中即驳回。
 * 可在本地零依赖跑通审核链路，作为生产 AI 审核的可替换底座。
 */
@Service
@Primary
@ConditionalOnProperty(name = "idlefish.audit.mock", havingValue = "true", matchIfMissing = true)
public class MockContentAuditServiceImpl implements ContentAuditService {

    @Override
    public AuditResult audit(String title, String description, List<String> images) {
        String text = (title == null ? "" : title) + " " + (description == null ? "" : description);
        if (text.isBlank()) {
            return AuditResult.pass();
        }
        if (SensitiveWords.hit(text)) {
            return AuditResult.reject("内容命中敏感词或站外联系方式，已拦截");
        }
        return AuditResult.pass();
    }
}
