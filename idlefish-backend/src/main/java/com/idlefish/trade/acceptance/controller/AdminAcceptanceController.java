package com.idlefish.trade.acceptance.controller;

import com.idlefish.trade.acceptance.closure.ClosureResult;
import com.idlefish.trade.acceptance.dto.AcceptanceDefectCreateDTO;
import com.idlefish.trade.acceptance.dto.AcceptanceResultDTO;
import com.idlefish.trade.acceptance.entity.AcceptanceCase;
import com.idlefish.trade.acceptance.entity.AcceptanceDefect;
import com.idlefish.trade.acceptance.service.AcceptanceService;
import com.idlefish.trade.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 真机验收闭环管理后台接口（ITER-F01 / F-11.5）。
 * 与既有 /api/admin/* 同域，受统一鉴权。真机环境由运营/测试在商户号侧执行，
 * 结果通过本接口回填；缺陷经 REQ-09 登记并回链 PRD。
 */
@RestController
@RequestMapping("/api/admin/acceptance")
public class AdminAcceptanceController {

    private final AcceptanceService acceptanceService;

    public AdminAcceptanceController(AcceptanceService acceptanceService) {
        this.acceptanceService = acceptanceService;
    }

    /** 幂等构造 PRD 验收用例（REQ-01~REQ-09）。 */
    @PostMapping("/seed")
    public Result<List<AcceptanceCase>> seed() {
        return Result.ok(acceptanceService.seedFromPrd());
    }

    /** 验收用例列表。 */
    @GetMapping("/cases")
    public Result<List<AcceptanceCase>> listCases() {
        return Result.ok(acceptanceService.listCases());
    }

    /** 单个验收用例。 */
    @GetMapping("/cases/{id}")
    public Result<AcceptanceCase> getCase(@PathVariable Long id) {
        return Result.ok(acceptanceService.getCase(id));
    }

    /** 回填用例验收结果（PASSED/FAILED/SKIPPED）。 */
    @PostMapping("/cases/{id}/result")
    public Result<AcceptanceCase> recordResult(@PathVariable Long id,
                                               @Valid @RequestBody AcceptanceResultDTO dto) {
        return Result.ok(acceptanceService.recordResult(id, dto));
    }

    /** 缺陷列表。 */
    @GetMapping("/defects")
    public Result<List<AcceptanceDefect>> listDefects() {
        return Result.ok(acceptanceService.listDefects());
    }

    /** 单个缺陷。 */
    @GetMapping("/defects/{id}")
    public Result<AcceptanceDefect> getDefect(@PathVariable Long id) {
        return Result.ok(acceptanceService.getDefect(id));
    }

    /** 登记真机验收缺陷（REQ-09 回链 PRD）。 */
    @PostMapping("/defects")
    public Result<AcceptanceDefect> registerDefect(@Valid @RequestBody AcceptanceDefectCreateDTO dto) {
        return Result.ok(acceptanceService.registerDefect(dto));
    }

    /** 标记缺陷已修复（OPEN→FIXED）。 */
    @PostMapping("/defects/{id}/fix")
    public Result<AcceptanceDefect> fixDefect(@PathVariable Long id) {
        return Result.ok(acceptanceService.fixDefect(id));
    }

    /** 闭环缺陷（FIXED→CLOSED）。 */
    @PostMapping("/defects/{id}/close")
    public Result<AcceptanceDefect> closeDefect(@PathVariable Long id) {
        return Result.ok(acceptanceService.closeDefect(id));
    }

    /** 查询当前验收闭环状态。 */
    @GetMapping("/closure")
    public Result<ClosureResult> closure() {
        return Result.ok(acceptanceService.evaluateClosure());
    }

    /** 关单：仅当全部用例通过且无未关闭缺陷时成功，否则抛 ACCEPTANCE_NOT_CLOSED。 */
    @PostMapping("/close")
    public Result<ClosureResult> close() {
        return Result.ok(acceptanceService.closeAcceptance());
    }
}
