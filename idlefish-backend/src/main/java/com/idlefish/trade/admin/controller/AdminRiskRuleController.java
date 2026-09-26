package com.idlefish.trade.admin.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.risk.entity.RiskRule;
import com.idlefish.trade.risk.service.RiskRuleEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风控规则运营后台（F-15.1）：可配置规则库的热加载管理。
 * 规则持久化于 t_risk_rule，保存/切换后由 {@link RiskRuleEngine} 热加载到活跃集，无需重启。
 */
@RestController
@RequestMapping("/api/admin/risk")
public class AdminRiskRuleController {

    private final RiskRuleEngine ruleEngine;

    public AdminRiskRuleController(RiskRuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    /** 当前活跃规则清单（按优先级升序）。 */
    @GetMapping("/rules")
    public Result<List<RiskRule>> rules(@CurrentAdmin AdminUser admin) {
        return Result.ok(ruleEngine.listActive());
    }

    /** 批量保存（新增/更新）规则并热加载。 */
    @PutMapping("/rules")
    public Result<Void> saveRules(@CurrentAdmin AdminUser admin, @RequestBody List<RiskRule> rules) {
        ruleEngine.saveRules(rules);
        return Result.ok();
    }

    /** 切换单条规则启用状态并热加载。 */
    @PostMapping("/rules/{code}/toggle")
    public Result<Void> toggle(@CurrentAdmin AdminUser admin, @PathVariable String code,
                              @RequestParam int enabled) {
        ruleEngine.toggle(code, enabled);
        return Result.ok();
    }
}
