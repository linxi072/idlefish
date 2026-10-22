package com.idlefish.trade.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 同城配送单状态机（ITER-F05 / REQ-06）：无副作用纯函数，便于离线单测。
 *
 * <p>主流程：CREATED（已下单待接单）→ ACCEPTED（骑手接单）→ DELIVERING（配送中）
 * → ARRIVED（已送达）→ SIGNED（已签收，终态）。
 * 旁路：CREATED/ACCEPTED 可取消 → CANCELED；任一阶段服务商不可用 → FAILED（REQ-09 明确提示）。
 */
public final class LocalDeliveryStateMachine {

    /** 已创建（已向服务商下单，等待骑手接单）。 */
    public static final String CREATED = "CREATED";
    /** 骑手已接单。 */
    public static final String ACCEPTED = "ACCEPTED";
    /** 配送中。 */
    public static final String DELIVERING = "DELIVERING";
    /** 已送达。 */
    public static final String ARRIVED = "ARRIVED";
    /** 已签收，终态。 */
    public static final String SIGNED = "SIGNED";
    /** 已取消，终态。 */
    public static final String CANCELED = "CANCELED";
    /** 失败（服务商不可用 / 超范围 / 拒接单），终态（REQ-09）。 */
    public static final String FAILED = "FAILED";

    private static final Set<String> OPEN_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(CREATED, ACCEPTED, DELIVERING, ARRIVED)));
    private static final Set<String> TERMINAL_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(SIGNED, CANCELED, FAILED)));

    private LocalDeliveryStateMachine() {
    }

    /** 是否未结。 */
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
            case CREATED:
                return ACCEPTED.equals(to) || CANCELED.equals(to) || FAILED.equals(to);
            case ACCEPTED:
                return DELIVERING.equals(to) || ARRIVED.equals(to) || CANCELED.equals(to) || FAILED.equals(to);
            case DELIVERING:
                return ARRIVED.equals(to) || FAILED.equals(to);
            case ARRIVED:
                return SIGNED.equals(to);
            default:
                return false;
        }
    }

    /** 可否取消（未送达前均可取消）。 */
    public static boolean canCancel(String status) {
        return CREATED.equals(status) || ACCEPTED.equals(status) || DELIVERING.equals(status);
    }

    /** 是否处于可回传轨迹的阶段（REQ-06 轨迹仅在配送中有效）。 */
    public static boolean canTrack(String status) {
        return isOpen(status) || SIGNED.equals(status);
    }
}
