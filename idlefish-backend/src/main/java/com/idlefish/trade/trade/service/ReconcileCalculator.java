package com.idlefish.trade.trade.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资金对账纯函数计算（ITER-T02 / REQ-06/07/08）：无状态、无副作用、long 分整型运算，便于离线单测。
 * 多维对账维度：PAY（PayOrder.success ↔ FundFlow PAY）、REFUND（Refund.REFUNDED ↔ FundFlow REFUND）、
 * SETTLE（Settlement.settled ↔ FundFlow SETTLE）。FREEZE 因提现无独立冻结终态而排除，避免误报。
 */
public final class ReconcileCalculator {

    /** 对账维度常量（与 FundFlow.type 及 ReconcileBill.dimension 对齐）。 */
    public static final String DIM_PAY = "PAY";
    public static final String DIM_REFUND = "REFUND";
    public static final String DIM_SETTLE = "SETTLE";

    private ReconcileCalculator() {
    }

    /** 金额差异：实际 - 预期（分）。 */
    public static long amountDiff(long expected, long actual) {
        return actual - expected;
    }

    /** 笔数差异：实际 - 预期。 */
    public static int countDiff(int expected, int actual) {
        return actual - expected;
    }

    /** 是否平账：笔数一致且金额一致。 */
    public static boolean matched(long expectedAmount, long actualAmount, int expectedCount, int actualCount) {
        return expectedAmount == actualAmount && expectedCount == actualCount;
    }

    /**
     * 按业务单号逐笔比对（REQ-07 下钻）：将平台侧金额映射与资金流水侧金额映射按 bizNo 对齐，
     * 输出仅包含「存在差异」的明细（金额或笔数不一致）。无差异笔返回空列表。
     *
     * @param dimension   维度标识（PAY/REFUND/SETTLE）
     * @param expectedMap 平台侧 map：bizNo -> 期望金额（分）
     * @param actualMap   资金流水侧 map：bizNo -> 实际金额（分）
     * @return 差异明细记录（仅含不一致项），字段含义见 {@link ReconcileDiffInput}
     */
    public static List<ReconcileDiffInput> diffByBizNo(String dimension,
                                                       Map<String, Long> expectedMap,
                                                       Map<String, Long> actualMap) {
        List<ReconcileDiffInput> diffs = new ArrayList<>();
        Set<String> allBiz = new HashSet<>(expectedMap.keySet());
        allBiz.addAll(actualMap.keySet());
        for (String bizNo : allBiz) {
            boolean expPresent = expectedMap.containsKey(bizNo);
            boolean actPresent = actualMap.containsKey(bizNo);
            long exp = expPresent ? expectedMap.get(bizNo) : 0L;
            long act = actPresent ? actualMap.get(bizNo) : 0L;
            int expCount = expPresent ? 1 : 0;
            int actCount = actPresent ? 1 : 0;
            if (exp != act || expCount != actCount) {
                diffs.add(new ReconcileDiffInput(dimension, bizNo, exp, act, expCount, actCount));
            }
        }
        return diffs;
    }

    /**
     * 逐笔差异输入的不可变值类型（Java 17 record）：仅承载维度/单号/金额/笔数，
     * 由调用方转换并落库为 {@code ReconcileDiff} 实体，保持计算器零耦合。
     */
    public record ReconcileDiffInput(String dimension, String bizNo,
                                     long expectedAmount, long actualAmount,
                                     int expectedCount, int actualCount) {
        public long diffAmount() {
            return amountDiff(expectedAmount, actualAmount);
        }
    }
}
