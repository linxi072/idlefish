package com.idlefish.trade.datascope.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.datascope.DataScopeDecision;
import com.idlefish.trade.datascope.dto.DataScopeAdminDTO;
import com.idlefish.trade.datascope.entity.DataScopeTempGrant;
import com.idlefish.trade.datascope.service.DataScopeService;
import com.idlefish.trade.datascope.service.TempGrantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 数据范围管理后台（ITER-T05 / REQ-02/03/05/06/09/10）。
 * 提供「我的范围 / 范围内机构 / 越权预览 / 临时授权 / 授权列表 / 撤销 / 归属迁移」能力。
 * 鉴权沿用现有 RBAC 拦截器（{@code AdminAuthInterceptor}），本控制器仅聚焦数据范围自身。
 */
@RestController
@RequestMapping("/api/admin/data-scope")
public class AdminDataScopeController {

    private final DataScopeService dataScopeService;
    private final TempGrantService tempGrantService;

    public AdminDataScopeController(DataScopeService dataScopeService, TempGrantService tempGrantService) {
        this.dataScopeService = dataScopeService;
        this.tempGrantService = tempGrantService;
    }

    /** 当前管理员的可见范围决议（范围 + 机构集合）。 */
    @GetMapping("/my-scope")
    public Result<DataScopeDecision> myScope(@CurrentAdmin AdminUser admin) {
        return Result.ok(dataScopeService.resolveForAdmin(admin.getId()));
    }

    /** 当前管理员可见的机构 ID 集合（ALL 时返回全量机构）。 */
    @GetMapping("/orgs-in-scope")
    public Result<Set<Long>> orgsInScope(@CurrentAdmin AdminUser admin) {
        return Result.ok(dataScopeService.orgsInScope(admin.getId()));
    }

    /** 越权预览：目标机构是否在当前管理员可见范围内。 */
    @PostMapping("/preview")
    public Result<Boolean> preview(@CurrentAdmin AdminUser admin, @Valid @RequestBody DataScopeAdminDTO.PreviewRequest req) {
        return Result.ok(dataScopeService.isAccessible(admin.getId(), req.targetOrgId));
    }

    /** 创建临时授权（开放额外机构可见范围，限有效期）。 */
    @PostMapping("/temp-grant")
    public Result<DataScopeTempGrant> tempGrant(@CurrentAdmin AdminUser admin,
                                                @Valid @RequestBody DataScopeAdminDTO.TempGrantRequest req) {
        Set<Long> orgIds = req.orgIds == null ? Set.of() : Set.copyOf(req.orgIds);
        DataScopeTempGrant g = tempGrantService.create(admin.getId(), orgIds, req.expiredAt);
        return Result.ok(g);
    }

    /** 当前管理员生效中的临时授权列表。 */
    @GetMapping("/temp-grants")
    public Result<List<DataScopeTempGrant>> tempGrants(@CurrentAdmin AdminUser admin) {
        return Result.ok(tempGrantService.listActive(admin.getId(), java.time.LocalDateTime.now()));
    }

    /** 撤销临时授权。 */
    @PostMapping("/revoke")
    public Result<Void> revoke(@CurrentAdmin AdminUser admin, @RequestParam String grantNo) {
        tempGrantService.revoke(grantNo);
        return Result.ok();
    }

    /** 数据归属迁移（REQ-10）：将源机构数据重指派到目标机构，返回受影响行数。 */
    @PostMapping("/migrate")
    public Result<Integer> migrate(@CurrentAdmin AdminUser admin,
                                  @Valid @RequestBody DataScopeAdminDTO.MigrateRequest req) {
        int affected = dataScopeService.migrateData(admin.getId(), req.fromOrgId, req.toOrgId);
        return Result.ok(affected);
    }
}
