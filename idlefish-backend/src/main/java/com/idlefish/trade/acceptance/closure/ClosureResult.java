package com.idlefish.trade.acceptance.closure;

/**
 * 验收闭环计算结果（ITER-F01 纯函数产出，不可变值对象）。
 */
public record ClosureResult(
        long totalCases,
        long passedCases,
        long openCases,
        long totalDefects,
        long openDefects,
        ClosureStatus status,
        boolean closable
) {
}
