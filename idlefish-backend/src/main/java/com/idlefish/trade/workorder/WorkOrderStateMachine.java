package com.idlefish.trade.workorder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 客服工单状态机（F-12）：无副作用纯函数，便于离线单测。
 *
 * <p>主流程：OPEN（待受理）→ IN_PROGRESS（处理中）/ WAITING_USER（待用户补充）→ RESOLVED（已解决）→ CLOSED（已关闭）。
 * 任意未结状态（OPEN/IN_PROGRESS/WAITING_USER）均可直接流转到 RESOLVED 或 CLOSED（用户撤销/关闭或管理员关单）。
 * RESOLVED 为半终态，仅可流转到 CLOSED。CLOSED 为终态，不可逆。
 */
public final class WorkOrderStateMachine {

    public static final String OPEN = "OPEN";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String WAITING_USER = "WAITING_USER";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";

    private static final Set<String> OPEN_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(OPEN, IN_PROGRESS, WAITING_USER)));
    private static final Set<String> TERMINAL_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(CLOSED)));

    private WorkOrderStateMachine() {
    }

    /** 是否未结（仍在客服处理流程中）。 */
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
            case OPEN:
                // 待受理：分配处理 / 待用户补充 / 直接解决 / 直接关闭（用户撤销或管理员关单）
                return IN_PROGRESS.equals(to) || WAITING_USER.equals(to)
                        || RESOLVED.equals(to) || CLOSED.equals(to);
            case IN_PROGRESS:
                // 处理中：待用户补充 / 解决 / 关闭
                return WAITING_USER.equals(to) || RESOLVED.equals(to) || CLOSED.equals(to);
            case WAITING_USER:
                // 待用户补充：继续处理 / 解决 / 关闭
                return IN_PROGRESS.equals(to) || RESOLVED.equals(to) || CLOSED.equals(to);
            case RESOLVED:
                // 已解决：仅可关闭归档
                return CLOSED.equals(to);
            case CLOSED:
                return false;
            default:
                return false;
        }
    }
}
