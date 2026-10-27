package com.idlefish.trade.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.batch.BatchInsertService;
import com.idlefish.trade.risk.entity.RiskRule;
import com.idlefish.trade.risk.mapper.RiskRuleMapper;
import com.idlefish.trade.risk.mapper.TrackEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RiskRuleEngine.saveRules 重构后行为验证（D4 债务修复）：
 * 仅当规则 id 为空（新增）走批量插入；已存在（更新）仍逐条 updateById，保持 upsert 语义。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskRuleEngineTest {

    @Mock
    private RiskRuleMapper ruleMapper;
    @Mock
    private TrackEventMapper trackEventMapper;
    @Mock
    private DeviceFingerprintService deviceFingerprintService;
    @Mock
    private IdlefishProperties props;
    @Mock
    private BatchInsertService batchInsertService;

    @InjectMocks
    private RiskRuleEngine engine;

    private static RiskRule rule(Long id, String code) {
        RiskRule r = new RiskRule();
        r.setId(id);
        r.setCode(code);
        return r;
    }

    @Test
    void saveRules_splitsInsertAndUpdate() {
        // reload() 内部会触发 selectList，置空避免种子逻辑干扰断言
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(batchInsertService.batchInsert(eq(RiskRule.class), eq(RiskRuleMapper.class), any(List.class)))
                .thenReturn(2);

        List<RiskRule> rules = List.of(
                rule(null, "NEW_A"),
                rule(null, "NEW_B"),
                rule(99L, "EXISTING"));
        engine.saveRules(rules);

        ArgumentCaptor<List<RiskRule>> insertCaptor = ArgumentCaptor.forClass(List.class);
        verify(batchInsertService).batchInsert(eq(RiskRule.class), eq(RiskRuleMapper.class), insertCaptor.capture());
        assertEquals(2, insertCaptor.getValue().size());
        for (RiskRule r : insertCaptor.getValue()) {
            assertEquals(null, r.getId());
        }

        ArgumentCaptor<RiskRule> updateCaptor = ArgumentCaptor.forClass(RiskRule.class);
        verify(ruleMapper).updateById(updateCaptor.capture());
        assertEquals(99L, updateCaptor.getValue().getId());
    }

    @Test
    void saveRules_onlyUpdates_doesNotBatchInsert() {
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        engine.saveRules(List.of(rule(7L, "ONLY_UPDATE")));

        verify(batchInsertService, never()).batchInsert(any(), any(), any());
        ArgumentCaptor<RiskRule> updateCaptor = ArgumentCaptor.forClass(RiskRule.class);
        verify(ruleMapper).updateById(updateCaptor.capture());
        assertEquals(7L, updateCaptor.getValue().getId());
    }

    @Test
    void saveRules_nullRules_isNoOp() {
        engine.saveRules(null);

        verify(batchInsertService, never()).batchInsert(any(), any(), any());
        verify(ruleMapper, never()).updateById(any(RiskRule.class));
    }

    @Test
    void saveRules_emptyInsertList_doesNotBatchInsert() {
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        engine.saveRules(List.of());

        verify(batchInsertService, never()).batchInsert(any(), any(), any());
        verify(ruleMapper, never()).updateById(any(RiskRule.class));
        assertNotNull(engine);
    }
}
