package com.idlefish.trade.acceptance.closure;

import com.idlefish.trade.acceptance.entity.AcceptanceCase;
import com.idlefish.trade.acceptance.entity.AcceptanceDefect;

import java.util.List;

/**
 * 验收闭环计算器（ITER-F01 纯函数，无 Spring 依赖、无副作用）。
 * <p>
 * 依据「验收用例集合」与「缺陷集合」推导闭环状态，恒等、可单测、可重入。
 * 任何入参为 {@code null} 均按空集合处理，保证空安全。
 *
 * <p>推导规则：
 * <ol>
 *   <li>存在用例且含未通过（NOT_RUN / FAILED）→ {@link ClosureStatus#OPEN_CASES}；</li>
 *   <li>否则存在未关闭缺陷（OPEN / FIXED）→ {@link ClosureStatus#OPEN_DEFECTS}；</li>
 *   <li>否则存在用例且全部通过（或 SKIPPED）→ {@link ClosureStatus#CLOSED}；</li>
 *   <li>否则（未登记任何用例）→ {@link ClosureStatus#NOT_RUN}。</li>
 * </ol>
 * 注：SKIPPED 视为「沙箱不适用、已确认跳过」，不阻断闭环（与 PRD 真机执行留待用户侧一致）。
 */
public final class AcceptanceClosureCalculator {

    /** 视为「未通过」的用例状态。 */
    private static final List<String> OPEN_CASE_STATUSES = List.of("NOT_RUN", "FAILED");

    private AcceptanceClosureCalculator() {
    }

    public static ClosureResult evaluate(List<AcceptanceCase> cases, List<AcceptanceDefect> defects) {
        List<AcceptanceCase> cs = cases == null ? List.of() : cases;
        List<AcceptanceDefect> ds = defects == null ? List.of() : defects;

        long totalCases = cs.size();
        long passedCases = cs.stream().filter(c -> "PASSED".equals(c.getStatus())).count();
        long openCases = cs.stream()
                .filter(c -> c.getStatus() != null && OPEN_CASE_STATUSES.contains(c.getStatus()))
                .count();
        long totalDefects = ds.size();
        long openDefects = ds.stream()
                .filter(d -> d.getStatus() != null && !"CLOSED".equals(d.getStatus()))
                .count();

        ClosureStatus status;
        if (!cs.isEmpty() && openCases > 0) {
            status = ClosureStatus.OPEN_CASES;
        } else if (!ds.isEmpty() && openDefects > 0) {
            status = ClosureStatus.OPEN_DEFECTS;
        } else if (!cs.isEmpty()) {
            // 用例全部 PASSED 或 SKIPPED，且无未关闭缺陷 → 闭环
            status = ClosureStatus.CLOSED;
        } else {
            status = ClosureStatus.NOT_RUN;
        }

        return new ClosureResult(
                totalCases, passedCases, openCases,
                totalDefects, openDefects,
                status, status.isClosable()
        );
    }
}
