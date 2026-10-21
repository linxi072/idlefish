package com.idlefish.trade.acceptance;

import com.idlefish.trade.acceptance.closure.ClosureResult;
import com.idlefish.trade.acceptance.closure.ClosureStatus;
import com.idlefish.trade.acceptance.dto.AcceptanceDefectCreateDTO;
import com.idlefish.trade.acceptance.dto.AcceptanceResultDTO;
import com.idlefish.trade.acceptance.entity.AcceptanceCase;
import com.idlefish.trade.acceptance.entity.AcceptanceDefect;
import com.idlefish.trade.acceptance.mapper.AcceptanceCaseMapper;
import com.idlefish.trade.acceptance.mapper.AcceptanceDefectMapper;
import com.idlefish.trade.acceptance.service.AcceptanceService;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验收闭环服务单测（ITER-F01）：Mockito 桩接两个 Mapper，覆盖
 * seed 幂等 / 结果回填校验 / 缺陷登记与状态机 / 闭环计算与关单门禁。
 */
@ExtendWith(MockitoExtension.class)
class AcceptanceServiceTest {

    @Mock
    private AcceptanceCaseMapper caseMapper;
    @Mock
    private AcceptanceDefectMapper defectMapper;
    @InjectMocks
    private AcceptanceService service;

    // ===================== seed 幂等 =====================

    @Test
    void seedFromPrd_skipWhenExists() {
        when(caseMapper.selectCount(any())).thenReturn(100L);
        List<AcceptanceCase> existing = List.of(new AcceptanceCase());
        when(caseMapper.selectList(any())).thenReturn(existing);

        List<AcceptanceCase> result = service.seedFromPrd();

        assertEquals(existing, result);
        verify(caseMapper, never()).insert((AcceptanceCase) any());
    }

    @Test
    void seedFromPrd_insertsNineWhenEmpty() {
        when(caseMapper.selectCount(any())).thenReturn(0L);

        List<AcceptanceCase> result = service.seedFromPrd();

        assertEquals(9, result.size());
        verify(caseMapper, times(9)).insert((AcceptanceCase) any());
    }

    // ===================== 用例 =====================

    @Test
    void getCase_notFound_throws() {
        when(caseMapper.selectById(anyLong())).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.getCase(1L));
        assertEquals(Code.ACCEPTANCE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getCase_found() {
        AcceptanceCase c = new AcceptanceCase();
        c.setId(5L);
        when(caseMapper.selectById(5L)).thenReturn(c);
        assertEquals(5L, service.getCase(5L).getId());
    }

    @Test
    void recordResult_updatesStatusAndTime() {
        AcceptanceCase c = new AcceptanceCase();
        c.setId(7L);
        c.setStatus("NOT_RUN");
        when(caseMapper.selectById(7L)).thenReturn(c);

        AcceptanceResultDTO dto = new AcceptanceResultDTO();
        dto.setResult("PASSED");
        dto.setExecutor("tester");

        AcceptanceCase r = service.recordResult(7L, dto);
        assertEquals("PASSED", r.getStatus());
        assertNotNull(r.getExecutedAt());
        verify(caseMapper).updateById(c);
    }

    @Test
    void recordResult_invalidResult_throws() {
        AcceptanceCase c = new AcceptanceCase();
        c.setId(8L);
        when(caseMapper.selectById(8L)).thenReturn(c);

        AcceptanceResultDTO dto = new AcceptanceResultDTO();
        dto.setResult("BOGUS");
        BizException ex = assertThrows(BizException.class, () -> service.recordResult(8L, dto));
        assertEquals(Code.PARAM_INVALID.getCode(), ex.getCode());
    }

    // ===================== 缺陷 =====================

    @Test
    void registerDefect_defaultsPrdAndOpen() {
        AcceptanceDefectCreateDTO dto = new AcceptanceDefectCreateDTO();
        dto.setReqCode("REQ-05");
        dto.setTitle("无凭据未硬网关出款");
        dto.setSeverity("P0");

        service.registerDefect(dto);

        ArgumentCaptor<AcceptanceDefect> cap = ArgumentCaptor.forClass(AcceptanceDefect.class);
        verify(defectMapper).insert(cap.capture());
        AcceptanceDefect d = cap.getValue();
        assertEquals("OPEN", d.getStatus());
        assertEquals(AcceptanceService.DEFAULT_PRD, d.getLinkedPrd());
        assertEquals("P0", d.getSeverity());
    }

    @Test
    void fixDefect_openToFixed() {
        AcceptanceDefect d = new AcceptanceDefect();
        d.setId(3L);
        d.setStatus("OPEN");
        when(defectMapper.selectById(3L)).thenReturn(d);

        AcceptanceDefect r = service.fixDefect(3L);
        assertEquals("FIXED", r.getStatus());
        assertNotNull(r.getFixedAt());
        verify(defectMapper).updateById(d);
    }

    @Test
    void fixDefect_nonOpen_throws() {
        AcceptanceDefect d = new AcceptanceDefect();
        d.setId(4L);
        d.setStatus("FIXED");
        when(defectMapper.selectById(4L)).thenReturn(d);

        BizException ex = assertThrows(BizException.class, () -> service.fixDefect(4L));
        assertEquals(Code.STATE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    void closeDefect_fixedToClosed() {
        AcceptanceDefect d = new AcceptanceDefect();
        d.setId(5L);
        d.setStatus("FIXED");
        when(defectMapper.selectById(5L)).thenReturn(d);

        AcceptanceDefect r = service.closeDefect(5L);
        assertEquals("CLOSED", r.getStatus());
        assertNotNull(r.getClosedAt());
        verify(defectMapper).updateById(d);
    }

    @Test
    void closeDefect_nonFixed_throws() {
        AcceptanceDefect d = new AcceptanceDefect();
        d.setId(6L);
        d.setStatus("OPEN");
        when(defectMapper.selectById(6L)).thenReturn(d);

        BizException ex = assertThrows(BizException.class, () -> service.closeDefect(6L));
        assertEquals(Code.STATE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    // ===================== 闭环 =====================

    @Test
    void evaluateClosure_closedWhenAllPassedNoDefects() {
        AcceptanceCase c = new AcceptanceCase();
        c.setStatus("PASSED");
        when(caseMapper.selectList(any())).thenReturn(List.of(c));
        when(defectMapper.selectList(any())).thenReturn(List.of());

        ClosureResult r = service.evaluateClosure();
        assertEquals(ClosureStatus.CLOSED, r.status());
        assertTrue(r.closable());
    }

    @Test
    void closeAcceptance_blockedWhenOpenCases() {
        AcceptanceCase c = new AcceptanceCase();
        c.setStatus("NOT_RUN");
        when(caseMapper.selectList(any())).thenReturn(List.of(c));
        when(defectMapper.selectList(any())).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.closeAcceptance());
        assertEquals(Code.ACCEPTANCE_NOT_CLOSED.getCode(), ex.getCode());
    }

    @Test
    void closeAcceptance_successWhenClosed() {
        AcceptanceCase c = new AcceptanceCase();
        c.setStatus("PASSED");
        when(caseMapper.selectList(any())).thenReturn(List.of(c));
        when(defectMapper.selectList(any())).thenReturn(List.of());

        ClosureResult r = service.closeAcceptance();
        assertEquals(ClosureStatus.CLOSED, r.status());
    }
}
