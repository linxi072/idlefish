package com.idlefish.trade.search.enhance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.search.enhance.dto.AbExperimentCreateDTO;
import com.idlefish.trade.search.enhance.dto.AbExperimentUpdateDTO;
import com.idlefish.trade.search.enhance.dto.BucketResult;
import com.idlefish.trade.search.enhance.entity.AbExperiment;
import com.idlefish.trade.search.enhance.mapper.AbExperimentMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AB 实验服务单测（ITER-F04 / REQ-06 冲突校验、REQ-07 一键回滚）。
 * Mock Mapper，使用真实 ObjectMapper（仅用于策略参数 JSON 校验/解析）。
 */
class AbExperimentServiceTest {

    private final AbExperimentMapper mapper = mock(AbExperimentMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MetricsRegistry metrics = mock(MetricsRegistry.class);
    private final AbExperimentService service = new AbExperimentService(mapper, objectMapper, metrics);

    private AbExperimentCreateDTO createDto(String name, int percent, String params,
                                            LocalDateTime start, LocalDateTime end) {
        AbExperimentCreateDTO d = new AbExperimentCreateDTO();
        d.setName(name);
        d.setTreatmentPercent(percent);
        d.setStrategyParams(params);
        d.setStartAt(start);
        d.setEndAt(end);
        return d;
    }

    @Test
    @DisplayName("创建：名称唯一校验，重复名称抛 AB_EXPERIMENT_CONFLICT")
    void createDuplicateName() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L); // 已存在同名
        BizException ex = assertThrows(BizException.class,
                () -> service.create(createDto("exp1", 30, null, null, null)));
        assertEquals(Code.AB_EXPERIMENT_CONFLICT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("创建：正常落库，初始 DRAFT")
    void createOk() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(mapper.insert(any(AbExperiment.class))).thenAnswer(inv -> {
            AbExperiment e = inv.getArgument(0);
            e.setId(1L);
            return 1;
        });
        AbExperiment e = service.create(createDto("exp2", 40, "{\"recallBoost\":1.2}", null, null));
        assertEquals("DRAFT", e.getStatus());
        assertEquals(1L, e.getId());
        verify(mapper).insert(any(AbExperiment.class));
    }

    @Test
    @DisplayName("创建：非法 JSON 策略参数抛 BIZ_ERROR")
    void createBadJson() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertThrows(BizException.class,
                () -> service.create(createDto("exp3", 40, "{not json}", null, null)));
    }

    @Test
    @DisplayName("时间窗重叠校验：与 RUNNING 实验重叠抛 AB_EXPERIMENT_CONFLICT")
    void windowOverlap() {
        AbExperiment running = new AbExperiment();
        running.setId(9L);
        running.setName("existing");
        running.setStatus("RUNNING");
        running.setStartAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        running.setEndAt(LocalDateTime.of(2026, 12, 31, 0, 0));
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(running));
        BizException ex = assertThrows(BizException.class,
                () -> service.create(createDto("exp4", 30, null,
                        LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 6, 30, 0, 0))));
        assertEquals(Code.AB_EXPERIMENT_CONFLICT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("获取不存在抛 AB_EXPERIMENT_NOT_FOUND")
    void getNotFound() {
        when(mapper.selectById(1L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.get(1L));
        assertEquals(Code.AB_EXPERIMENT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("一键回滚：RUNNING→ROLLED_BACK，幂等（已回滚再回滚 no-op）")
    void rollbackIdempotent() {
        AbExperiment e = new AbExperiment();
        e.setId(2L);
        e.setName("exp");
        e.setStatus("RUNNING");
        when(mapper.selectById(2L)).thenReturn(e);
        AbExperiment r = service.rollback(2L);
        assertEquals("ROLLED_BACK", r.getStatus());
        verify(mapper).updateById(any(AbExperiment.class));

        AbExperiment e2 = new AbExperiment();
        e2.setId(2L);
        e2.setName("exp");
        e2.setStatus("ROLLED_BACK");
        when(mapper.selectById(2L)).thenReturn(e2);
        service.rollback(2L); // 幂等，不抛异常
    }

    @Test
    @DisplayName("回滚非运行实验抛 BIZ_ERROR")
    void rollbackNonRunning() {
        AbExperiment e = new AbExperiment();
        e.setId(3L);
        e.setName("exp");
        e.setStatus("DRAFT");
        when(mapper.selectById(3L)).thenReturn(e);
        BizException ex = assertThrows(BizException.class, () -> service.rollback(3L));
        assertEquals(Code.BIZ_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("更新：仅非空字段生效")
    void updatePartial() {
        AbExperiment e = new AbExperiment();
        e.setId(4L);
        e.setName("exp");
        e.setStatus("DRAFT");
        e.setTreatmentPercent(10);
        e.setDescription("old");
        when(mapper.selectById(4L)).thenReturn(e);
        AbExperimentUpdateDTO d = new AbExperimentUpdateDTO();
        d.setDescription("new"); // 仅改描述
        AbExperiment r = service.update(4L, d);
        assertEquals("new", r.getDescription());
        assertEquals(10, r.getTreatmentPercent()); // 未变
        verify(mapper).updateById(any(AbExperiment.class));
    }

    @Test
    @DisplayName("分桶：实验生效且全量实验组返回 TREATMENT/effective=true")
    void assignBucketEffective() {
        AbExperiment e = new AbExperiment();
        e.setId(5L);
        e.setName("exp");
        e.setStatus("RUNNING");
        e.setTreatmentPercent(100); // 全量实验，必然 TREATMENT
        when(mapper.selectById(5L)).thenReturn(e);
        BucketResult b = service.assignBucket(123L, 5L);
        assertEquals("TREATMENT", b.group());
        assertEquals(true, b.effective());
    }
}
