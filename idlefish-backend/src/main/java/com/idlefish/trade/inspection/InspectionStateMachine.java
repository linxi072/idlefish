package com.idlefish.trade.inspection;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 鉴定验货单状态机（F-02）：无副作用纯函数，便于离线单测。
 *
 * <p>主流程：WAIT_PICKUP（待收件）→ IN_TRANSIT（运输中）→ INSPECTING（验货中）
 * → PASSED（已验通过） | REJECTED（已验不通过） | EXCEPTION（验货异常） | CANCELED（已取消）。
 *
 * <p>资金/履约约定：
 * <ul>
 *   <li>REQ-04 验货中冻结发货：isShippingFrozen 在「未结（OPEN）」态恒为 true，订单发货须被拦截；</li>
 *   <li>REQ-09 机构超时/不可用：timeoutTarget 将运输中/验货中归并为 EXCEPTION，
 *       且<b>绝不会</b>误判为通过（PASSED 仅能由机构显式回传触发）。</li>
 * </ul>
 */
public final class InspectionStateMachine {

    public static final String WAIT_PICKUP = "WAIT_PICKUP";
    public static final String IN_TRANSIT = "IN_TRANSIT";
    public static final String INSPECTING = "INSPECTING";
    public static final String PASSED = "PASSED";
    public static final String REJECTED = "REJECTED";
    public static final String EXCEPTION = "EXCEPTION";
    public static final String CANCELED = "CANCELED";

    private static final Set<String> OPEN_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(WAIT_PICKUP, IN_TRANSIT, INSPECTING)));
    private static final Set<String> TERMINAL_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(PASSED, REJECTED, EXCEPTION, CANCELED)));

    private InspectionStateMachine() {
    }

    /** 是否未结（仍在验货流程中：待收件 / 运输中 / 验货中）。 */
    public static boolean isOpen(String status) {
        return status != null && OPEN_STATUS.contains(status);
    }

    /** 是否终态。 */
    public static boolean isTerminal(String status) {
        return status != null && TERMINAL_STATUS.contains(status);
    }

    /**
     * REQ-04 发货冻结窗口：验货单未结（待收件/运输中/验货中）期间，关联订单禁止发货。
     * 一旦到达终态（通过/不通过/异常/取消），放行原履约流程。
     */
    public static boolean isShippingFrozen(String status) {
        return isOpen(status);
    }

    /** 判定状态转移是否允许。 */
    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        switch (from) {
            case WAIT_PICKUP:
                // 待收件：机构收件 / 买家取消
                return IN_TRANSIT.equals(to) || CANCELED.equals(to);
            case IN_TRANSIT:
                // 运输中：开始验货 / 超时异常 / 取消
                return INSPECTING.equals(to) || EXCEPTION.equals(to) || CANCELED.equals(to);
            case INSPECTING:
                // 验货中：仅机构显式回传可终结（通过 / 不通过 / 异常），不得取消
                return PASSED.equals(to) || REJECTED.equals(to) || EXCEPTION.equals(to);
            case PASSED:
            case REJECTED:
            case EXCEPTION:
            case CANCELED:
                return false;
            default:
                return false;
        }
    }

    /** 机构收件（WAIT_PICKUP → IN_TRANSIT）。 */
    public static boolean canReceive(String status) {
        return WAIT_PICKUP.equals(status);
    }

    /** 机构开始验货（IN_TRANSIT → INSPECTING）。 */
    public static boolean canStartInspect(String status) {
        return IN_TRANSIT.equals(status);
    }

    /** 机构回传结果（INSPECTING → PASSED/REJECTED/EXCEPTION）。 */
    public static boolean canReceiveResult(String status) {
        return INSPECTING.equals(status);
    }

    /** 可取消（WAIT_PICKUP / IN_TRANSIT）。验货开始后不可取消。 */
    public static boolean canCancel(String status) {
        return WAIT_PICKUP.equals(status) || IN_TRANSIT.equals(status);
    }

    /**
     * REQ-09 超时/机构不可用终态映射（纯函数）。
     * 仅运输中/验货中可因超时归并为 EXCEPTION；其它态返回 null 表示无需转移。
     * 该方法<b>不会</b>返回 PASSED，杜绝超时误判通过。
     *
     * @return 超时后应有的终态（EXCEPTION），或 null（无需转移）
     */
    public static String timeoutTarget(String status) {
        if (IN_TRANSIT.equals(status) || INSPECTING.equals(status)) {
            return EXCEPTION;
        }
        return null;
    }
}
