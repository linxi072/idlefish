package com.idlefish.trade.search.enhance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.search.enhance.dto.AbExperimentCreateDTO;
import com.idlefish.trade.search.enhance.dto.AbExperimentUpdateDTO;
import com.idlefish.trade.search.enhance.dto.BucketResult;
import com.idlefish.trade.search.enhance.entity.AbExperiment;
import com.idlefish.trade.search.enhance.mapper.AbExperimentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AB 实验服务（ITER-F04 搜索推荐深化 / REQ-06 稳定分桶与冲突校验、REQ-07 一键回滚）。
 * <p>
 * - 创建时校验名称唯一 + 与 RUNNING 实验时间窗不重叠（冲突抛 {@link Code#AB_EXPERIMENT_CONFLICT}）；
 * - 取当前生效实验供搜索增强分桶；分桶委托纯函数 {@link BucketAssigner}（稳定、跨设备一致）；
 * - 回滚幂等：RUNNING→ROLLED_BACK；已回滚再回滚为 no-op；未运行实验回滚抛业务异常。
 */
@Service
public class AbExperimentService {

    private final AbExperimentMapper mapper;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metrics;

    public AbExperimentService(AbExperimentMapper mapper, ObjectMapper objectMapper, MetricsRegistry metrics) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    /** 创建实验（初始状态 DRAFT）。 */
    @Transactional(rollbackFor = Exception.class)
    public AbExperiment create(AbExperimentCreateDTO dto) {
        if (mapper.selectCount(new LambdaQueryWrapper<AbExperiment>().eq(AbExperiment::getName, dto.getName())) > 0) {
            throw new BizException(Code.AB_EXPERIMENT_CONFLICT, "实验名称已存在");
        }
        if (dto.getStartAt() != null && dto.getEndAt() != null) {
            checkWindowOverlap(dto.getStartAt(), dto.getEndAt(), null);
        }
        if (dto.getStrategyParams() != null && !dto.getStrategyParams().isBlank()) {
            validateJson(dto.getStrategyParams());
        }
        AbExperiment e = new AbExperiment();
        e.setName(dto.getName());
        e.setStatus("DRAFT");
        e.setTreatmentPercent(dto.getTreatmentPercent());
        e.setStrategyParams(dto.getStrategyParams());
        e.setStartAt(dto.getStartAt());
        e.setEndAt(dto.getEndAt());
        e.setCreator(dto.getCreator());
        e.setDescription(dto.getDescription());
        mapper.insert(e);
        metrics.increment("ab.experiment.create");
        return e;
    }

    /** 实验列表（按创建时间倒序）。 */
    public List<AbExperiment> list() {
        return mapper.selectList(new LambdaQueryWrapper<AbExperiment>().orderByDesc(AbExperiment::getCreatedAt));
    }

    /** 取实验（不存在抛 {@link Code#AB_EXPERIMENT_NOT_FOUND}）。 */
    public AbExperiment get(Long id) {
        AbExperiment e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(Code.AB_EXPERIMENT_NOT_FOUND);
        }
        return e;
    }

    /** 更新实验（仅非空字段）。 */
    @Transactional(rollbackFor = Exception.class)
    public AbExperiment update(Long id, AbExperimentUpdateDTO dto) {
        AbExperiment e = get(id);
        if (dto.getTreatmentPercent() != null) {
            e.setTreatmentPercent(dto.getTreatmentPercent());
        }
        if (dto.getStrategyParams() != null) {
            if (!dto.getStrategyParams().isBlank()) {
                validateJson(dto.getStrategyParams());
            }
            e.setStrategyParams(dto.getStrategyParams());
        }
        if (dto.getStartAt() != null) {
            e.setStartAt(dto.getStartAt());
        }
        if (dto.getEndAt() != null) {
            e.setEndAt(dto.getEndAt());
        }
        if (dto.getDescription() != null) {
            e.setDescription(dto.getDescription());
        }
        mapper.updateById(e);
        return e;
    }

    /** 一键回滚（REQ-07）：RUNNING→ROLLED_BACK，幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public AbExperiment rollback(Long id) {
        AbExperiment e = get(id);
        if ("ROLLED_BACK".equals(e.getStatus())) {
            return e; // 幂等 no-op
        }
        if (!"RUNNING".equals(e.getStatus())) {
            throw new BizException(Code.BIZ_ERROR, "仅运行中的实验可回滚");
        }
        e.setStatus("ROLLED_BACK");
        mapper.updateById(e);
        metrics.increment("ab.experiment.rollback");
        return e;
    }

    /** 按实验 ID 分桶（不存在抛 {@link Code#AB_EXPERIMENT_NOT_FOUND}）。 */
    public BucketResult assignBucket(Long userId, Long experimentId) {
        return assignTo(get(experimentId), userId);
    }

    /** 取当前生效实验：首个 RUNNING 且处于时间窗内者；无则 null。 */
    public AbExperiment currentEffective() {
        List<AbExperiment> running = mapper.selectList(
                new LambdaQueryWrapper<AbExperiment>().eq(AbExperiment::getStatus, "RUNNING"));
        LocalDateTime now = LocalDateTime.now();
        for (AbExperiment e : running) {
            if (isWithinWindow(e, now)) {
                return e;
            }
        }
        return null;
    }

    /** 对指定实验与用户分桶（非生效返回对照组）。 */
    public BucketResult assignTo(AbExperiment e, Long userId) {
        boolean effective = isEffective(e);
        BucketAssigner.Group g = effective
                ? BucketAssigner.assign(userId, e.getTreatmentPercent())
                : BucketAssigner.Group.CONTROL;
        return new BucketResult(e.getId(), e.getName(), g.name(), effective,
                e.getTreatmentPercent() == null ? 0 : e.getTreatmentPercent(),
                parseParams(e.getStrategyParams()));
    }

    /** 实验是否生效：RUNNING 且处于时间窗内。 */
    public boolean isEffective(AbExperiment e) {
        return e != null && "RUNNING".equals(e.getStatus()) && isWithinWindow(e, LocalDateTime.now());
    }

    private boolean isWithinWindow(AbExperiment e, LocalDateTime now) {
        if (e.getStartAt() == null && e.getEndAt() == null) {
            return true;
        }
        if (e.getStartAt() != null && now.isBefore(e.getStartAt())) {
            return false;
        }
        if (e.getEndAt() != null && now.isAfter(e.getEndAt())) {
            return false;
        }
        return true;
    }

    private void checkWindowOverlap(LocalDateTime start, LocalDateTime end, Long excludeId) {
        List<AbExperiment> running = mapper.selectList(
                new LambdaQueryWrapper<AbExperiment>().eq(AbExperiment::getStatus, "RUNNING"));
        for (AbExperiment e : running) {
            if (excludeId != null && excludeId.equals(e.getId())) {
                continue;
            }
            if (e.getStartAt() == null || e.getEndAt() == null) {
                continue; // 长期实验不参与重叠判定（简化）
            }
            boolean overlap = !end.isBefore(e.getStartAt()) && !start.isAfter(e.getEndAt());
            if (overlap) {
                throw new BizException(Code.AB_EXPERIMENT_CONFLICT, "与实验「" + e.getName() + "」时间窗重叠");
            }
        }
    }

    private void validateJson(String json) {
        try {
            objectMapper.readTree(json);
        } catch (IOException ex) {
            throw new BizException(Code.BIZ_ERROR, "strategyParams 不是合法 JSON");
        }
    }

    private Map<String, Object> parseParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException ex) {
            return Map.of();
        }
    }
}
