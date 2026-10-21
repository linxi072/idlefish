package com.idlefish.trade.acceptance;

import com.idlefish.trade.acceptance.service.AcceptanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 真机验收用例种子（ITER-F01）。
 * <p>
 * 应用启动时幂等构造 PRD 验收用例（REQ-01~REQ-09）；若验收表尚未建表（如灰度发布早期），
 * 以 try/catch 静默跳过，不阻断 Spring 启动（建表后下次启动自动补齐）。
 */
@Slf4j
@Component
@Order(100)
public class AcceptanceSeeder implements ApplicationRunner {

    private final AcceptanceService acceptanceService;

    public AcceptanceSeeder(AcceptanceService acceptanceService) {
        this.acceptanceService = acceptanceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = acceptanceService.seedFromPrd().size();
            log.info("[acceptance] 验收用例种子就绪，共 {} 条", n);
        } catch (Exception e) {
            // 表不存在等情况：跳过，不阻断启动
            log.warn("[acceptance] 验收用例种子跳过（表可能尚未初始化）：{}", e.getMessage());
        }
    }
}
