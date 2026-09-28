package com.idlefish.trade.dispute;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 维权工单状态机（F-17）：无副作用纯函数，便于离线单测。
 *
 * <p>主流程：PENDING（待卖家处理）→ SELLER_REPLIED（卖家已举证）→ PLATFORM（平台介入中）
 * → RESOLVED（已裁决）→ CLOSED（归档）。
 * 旁路：PENDING / SELLER_REPLIED 可由买家撤销为 CANCELED；平台亦可直接裁决（跳至 RESOLVED）。
 */
public final class DisputeStateMachine {

    public static final String PENDING = "PENDING";
    public static final String SELLER_REPLIED = "SELLER_REPLIED";
    public static final String PLATFORM = "PLATFORM";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELED = "CANCELED";

    private static final Set<String> OPEN_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(PENDING, SELLER_REPLIED, PLATFORM)));
    private static final Set<String> TERMINAL_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(RESOLVED, CLOSED, CANCELED)));

    private DisputeStateMachine() {
    }

    /** 是否未结（仍在维权流程中）。 */
    public static boolean isOpen(String status) {
        return status != null && OPEN_STATUS.contains(status);
    }

    /** 是否终态。 */
    public static boolean isTerminal(String status) {
        return status != null && TERMINAL_STATUS.contains(status);
    }

    /** 判定状态转移是否允许。 */
    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        switch (from) {
            case PENDING:
                // 待处理：卖家举证 / 申请平台介入 / 买家撤销 / 平台直接裁决
                return SELLER_REPLIED.equals(to) || PLATFORM.equals(to)
                        || CANCELED.equals(to) || RESOLVED.equals(to);
            case SELLER_REPLIED:
                // 已举证：申请平台介入 / 平台裁决 / 买家撤销
                return PLATFORM.equals(to) || RESOLVED.equals(to) || CANCELED.equals(to);
            case PLATFORM:
                // 平台介入中：仅可裁决
                return RESOLVED.equals(to);
            case RESOLVED:
                return CLOSED.equals(to);
            case CANCELED:
                return CLOSED.equals(to);
            case CLOSED:
                return false;
            default:
                return false;
        }
    }

    /** 买家可撤销（平台介入后不可撤销）。 */
    public static boolean canBuyerCancel(String status) {
        return PENDING.equals(status) || SELLER_REPLIED.equals(status);
    }

    /** 可申请平台介入。 */
    public static boolean canApplyPlatform(String status) {
        return PENDING.equals(status) || SELLER_REPLIED.equals(status);
    }

    /** 平台可裁决（待处理 / 已举证 / 介入中）。 */
    public static boolean canResolve(String status) {
        return PENDING.equals(status) || SELLER_REPLIED.equals(status) || PLATFORM.equals(status);
    }
}
