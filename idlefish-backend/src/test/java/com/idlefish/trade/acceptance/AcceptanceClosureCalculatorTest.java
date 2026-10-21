package com.idlefish.trade.acceptance;

import com.idlefish.trade.acceptance.closure.AcceptanceClosureCalculator;
import com.idlefish.trade.acceptance.closure.ClosureResult;
import com.idlefish.trade.acceptance.closure.ClosureStatus;
import com.idlefish.trade.acceptance.entity.AcceptanceCase;
import com.idlefish.trade.acceptance.entity.AcceptanceDefect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验收闭环纯函数单测（ITER-F01）：覆盖状态机推导与计数，离线可跑。
 */
class AcceptanceClosureCalculatorTest {

    private static AcceptanceCase caze(String status) {
        AcceptanceCase c = new AcceptanceCase();
        c.setStatus(status);
        return c;
    }

    private static AcceptanceDefect defect(String status) {
        AcceptanceDefect d = new AcceptanceDefect();
        d.setStatus(status);
        return d;
    }

    @Test
    void noCases_notRun() {
        ClosureResult r = AcceptanceClosureCalculator.evaluate(List.of(), List.of());
        assertEquals(ClosureStatus.NOT_RUN, r.status());
        assertFalse(r.closable());
    }

    @Test
    void nullInputs_notRunAndSafe() {
        ClosureResult r = AcceptanceClosureCalculator.evaluate(null, null);
        assertEquals(ClosureStatus.NOT_RUN, r.status());
        assertFalse(r.closable());
    }

    @Test
    void onlySkippedCases_closed() {
        // SKIPPED 视为沙箱不适用已确认跳过，不阻断闭环
        ClosureResult r = AcceptanceClosureCalculator.evaluate(
                List.of(caze("SKIPPED"), caze("SKIPPED")), List.of());
        assertEquals(ClosureStatus.CLOSED, r.status());
        assertTrue(r.closable());
    }

    @Test
    void notRunCase_openCases() {
        ClosureResult r = AcceptanceClosureCalculator.evaluate(
                List.of(caze("PASSED"), caze("NOT_RUN")), List.of());
        assertEquals(ClosureStatus.OPEN_CASES, r.status());
        assertFalse(r.closable());
        assertEquals(1, r.openCases());
    }

    @Test
    void failedCase_openCases() {
        ClosureResult r = AcceptanceClosureCalculator.evaluate(
                List.of(caze("FAILED")), List.of());
        assertEquals(ClosureStatus.OPEN_CASES, r.status());
        assertEquals(1, r.openCases());
    }

    @Test
    void allPassed_oneOpenDefect_openDefects() {
        ClosureResult r = AcceptanceClosureCalculator.evaluate(
                List.of(caze("PASSED"), caze("PASSED")),
                List.of(defect("OPEN")));
        assertEquals(ClosureStatus.OPEN_DEFECTS, r.status());
        assertFalse(r.closable());
        assertEquals(1, r.openDefects());
    }

    @Test
    void allPassed_oneFixedDefect_openDefects() {
        ClosureResult r = AcceptanceClosureCalculator.evaluate(
                List.of(caze("PASSED")), List.of(defect("FIXED")));
        assertEquals(ClosureStatus.OPEN_DEFECTS, r.status());
        assertEquals(1, r.openDefects());
    }

    @Test
    void allPassed_noDefects_closed() {
        ClosureResult r = AcceptanceClosureCalculator.evaluate(
                List.of(caze("PASSED"), caze("SKIPPED")), List.of());
        assertEquals(ClosureStatus.CLOSED, r.status());
        assertTrue(r.closable());
        assertEquals(2, r.totalCases());
        assertEquals(1, r.passedCases());
    }

    @Test
    void allPassed_closedDefect_closed() {
        ClosureResult r = AcceptanceClosureCalculator.evaluate(
                List.of(caze("PASSED")), List.of(defect("CLOSED")));
        assertEquals(ClosureStatus.CLOSED, r.status());
        assertTrue(r.closable());
    }

    @Test
    void countsAreAccurate() {
        ClosureResult r = AcceptanceClosureCalculator.evaluate(
                List.of(caze("PASSED"), caze("FAILED"), caze("NOT_RUN"), caze("SKIPPED")),
                List.of(defect("OPEN"), defect("FIXED"), defect("CLOSED")));
        assertEquals(4, r.totalCases());
        assertEquals(1, r.passedCases());
        assertEquals(2, r.openCases());
        assertEquals(3, r.totalDefects());
        assertEquals(2, r.openDefects());
        assertEquals(ClosureStatus.OPEN_CASES, r.status());
    }
}
