package com.idlefish.trade.compliance.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.web.CurrentUser;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.compliance.dto.ConsentDTO;
import com.idlefish.trade.compliance.dto.PreferenceDTO;
import com.idlefish.trade.compliance.service.ComplianceService;
import com.idlefish.trade.compliance.vo.ComplianceProfileVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合规中心用户端接口（ITER-U05，需登录）：档案查询 / 授权授予撤回 / 营销通知开关 / 关怀模式。
 *
 * <p>授权项在授予前由前端按 {@code purpose}/{@code scope} 明示（REQ-07）；撤回后立即生效（REQ-08）。
 */
@RestController
@RequestMapping("/api/compliance")
public class ComplianceController {

    private final ComplianceService complianceService;

    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    /** 合规档案：未成年人状态 + 偏好开关 + 授权清单。 */
    @GetMapping("/profile")
    public Result<ComplianceProfileVO> profile(@CurrentUser LoginUser user) {
        return Result.ok(complianceService.profile(user.getUserId()));
    }

    /** 授予某项个人信息收集授权（REQ-07）。 */
    @PostMapping("/consent/grant")
    public Result<Void> grant(@CurrentUser LoginUser user, @Valid @RequestBody ConsentDTO dto) {
        complianceService.grantConsent(user.getUserId(), dto.getConsentCode());
        return Result.ok();
    }

    /** 撤回某项授权（REQ-08）：撤回后相关功能停止，非静默继续使用。 */
    @PostMapping("/consent/revoke")
    public Result<Void> revoke(@CurrentUser LoginUser user, @Valid @RequestBody ConsentDTO dto) {
        complianceService.revokeConsent(user.getUserId(), dto.getConsentCode());
        return Result.ok();
    }

    /** 营销类通知开关（REQ-09）：关闭后不再下发营销消息，交易必需通知保留。 */
    @PostMapping("/notify/marketing")
    public Result<Void> marketingNotify(@CurrentUser LoginUser user, @Valid @RequestBody PreferenceDTO dto) {
        complianceService.setMarketingNotify(user.getUserId(), Boolean.TRUE.equals(dto.getEnabled()));
        return Result.ok();
    }

    /** 关怀模式（REQ-02，适老化偏好持久化）。 */
    @PostMapping("/care-mode")
    public Result<Void> careMode(@CurrentUser LoginUser user, @Valid @RequestBody PreferenceDTO dto) {
        complianceService.setCareMode(user.getUserId(), Boolean.TRUE.equals(dto.getEnabled()));
        return Result.ok();
    }
}
