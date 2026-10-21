package com.idlefish.trade.search.enhance.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.search.enhance.dto.AbExperimentCreateDTO;
import com.idlefish.trade.search.enhance.dto.AbExperimentUpdateDTO;
import com.idlefish.trade.search.enhance.dto.BucketResult;
import com.idlefish.trade.search.enhance.entity.AbExperiment;
import com.idlefish.trade.search.enhance.service.AbExperimentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AB 实验管理后台接口（ITER-F04 搜索推荐深化 / REQ-06 稳定分桶、REQ-07 一键回滚）。
 * <p>
 * 受统一后台鉴权（/api/admin/**）。运营可创建/查看/更新实验、一键回滚劣化实验、
 * 按「实验 + 用户」分桶以验证分组稳定性。分桶本身由纯函数 {@code BucketAssigner} 保证跨设备一致。
 */
@RestController
@RequestMapping("/api/admin/ab-experiment")
public class AdminAbExperimentController {

    private final AbExperimentService abExperimentService;

    public AdminAbExperimentController(AbExperimentService abExperimentService) {
        this.abExperimentService = abExperimentService;
    }

    /** 创建实验（初始 DRAFT；名称唯一 + 与 RUNNING 实验时间窗不重叠校验）。 */
    @PostMapping
    public Result<AbExperiment> create(@Valid @RequestBody AbExperimentCreateDTO dto) {
        return Result.ok(abExperimentService.create(dto));
    }

    /** 实验列表（按创建时间倒序）。 */
    @GetMapping
    public Result<List<AbExperiment>> list() {
        return Result.ok(abExperimentService.list());
    }

    /** 当前生效实验（RUNNING 且处于时间窗内；无则 data 为 null）。 */
    @GetMapping("/current")
    public Result<AbExperiment> current() {
        return Result.ok(abExperimentService.currentEffective());
    }

    /** 单个实验（不存在抛 AB_EXPERIMENT_NOT_FOUND(30029)）。 */
    @GetMapping("/{id}")
    public Result<AbExperiment> get(@PathVariable Long id) {
        return Result.ok(abExperimentService.get(id));
    }

    /** 更新实验（仅传入的非空字段生效）。 */
    @PostMapping("/{id}/update")
    public Result<AbExperiment> update(@PathVariable Long id,
                                       @Valid @RequestBody AbExperimentUpdateDTO dto) {
        return Result.ok(abExperimentService.update(id, dto));
    }

    /** 一键回滚（REQ-07）：RUNNING→ROLLED_BACK，幂等；非 RUNNING 抛 BIZ_ERROR(30006)。 */
    @PostMapping("/{id}/rollback")
    public Result<AbExperiment> rollback(@PathVariable Long id) {
        return Result.ok(abExperimentService.rollback(id));
    }

    /** 按「实验 + 用户」分桶（调试 / 验证分组是否稳定一致）。 */
    @PostMapping("/{id}/assign")
    public Result<BucketResult> assign(@PathVariable Long id,
                                       @RequestParam Long userId) {
        return Result.ok(abExperimentService.assignBucket(userId, id));
    }
}
