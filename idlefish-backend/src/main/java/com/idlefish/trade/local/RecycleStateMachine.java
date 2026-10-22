package com.idlefish.trade.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 回收单状态机（ITER-F05）：无副作用纯函数，便于离线单测。
 *
 * <p>主流程：PENDING_PICKUP（待上门取件）→ PICKED_UP（已取件）→ INSPECTING（质检中）
 * → QUOTED（质检完成待确认报价）→ CONFIRMED（已确认待放款）→ PAID（已放款，终态）。
 *
 * <p>旁路：
 * <ul>
 *   <li>REQ-03：PENDING_PICKUP 阶段用户可无条件、无费用取消 → CANCELED。</li>
 *   <li>REQ-05：QUOTED 阶段用户拒绝报价 → REJECTED（取消并退回物品，平台不得强制成交或扣费）。</li>
 *   <li>REQ-09：服务商不可用 / 城市未开通 → UNAVAILABLE（明确失败态，禁止静默吞掉异常）。</li>
 * </ul>
 */
public final class RecycleStateMachine {

    /** 待上门取件（唯一允许无费用取消的阶段，REQ-03）。 */
    public static final String PENDING_PICKUP = "PENDING_PICKUP";
    /** 已揽收（物品在平台/服务商侧）。 */
    public static final String PICKED_UP = "PICKED_UP";
    /** 质检中。 */
    public static final String INSPECTING = "INSPECTING";
    /** 质检完成，已给出最终报价，等待用户确认（REQ-04）。 */
    public static final String QUOTED = "QUOTED";
    /** 用户已接受报价，等待放款（REQ-07）。 */
    public static final String CONFIRMED = "CONFIRMED";
    /** 已放款，终态。 */
    public static final String PAID = "PAID";
    /** 已取消，终态。 */
    public static final String CANCELED = "CANCELED";
    /** 用户拒绝报价并退回，终态（REQ-05）。 */
    public static final String REJECTED = "REJECTED";
    /** 服务不可用（城市未开通/第三方不可用），终态失败（REQ-09）。 */
    public static final String UNAVAILABLE = "UNAVAILABLE";

    private static final Set<String> OPEN_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(PENDING_PICKUP, PICKED_UP, INSPECTING, QUOTED, CONFIRMED)));
    private static final Set<String> TERMINAL_STATUS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(PAID, CANCELED, REJECTED, UNAVAILABLE)));

    private RecycleStateMachine() {
    }

    /** 是否未结（仍在回收流程中）。 */
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
            case PENDING_PICKUP:
                // 待上门：正常揽收；用户取消（REQ-03）；服务商/城市不可用（REQ-09）
                return PICKED_UP.equals(to) || CANCELED.equals(to) || UNAVAILABLE.equals(to);
            case PICKED_UP:
                // 已揽收：进入质检；异常不可用（如物品与描述严重不符，按 UNAVAILABLE 归集待人工）
                return INSPECTING.equals(to) || UNAVAILABLE.equals(to);
            case INSPECTING:
                // 质检中：出报价；质检异常亦可归 — 统一收敛到 QUOTED/UNAVAILABLE
                return QUOTED.equals(to) || UNAVAILABLE.equals(to);
            case QUOTED:
                // 等确认：接受报价 → CONFIRMED；拒绝 → REJECTED（REQ-05 不得强制成交）；
                // 服务商/城市不可用 → UNAVAILABLE（REQ-09，明确告知且不扣费）
                return CONFIRMED.equals(to) || REJECTED.equals(to) || UNAVAILABLE.equals(to);
            case CONFIRMED:
                // 待放款：放款成功 → PAID；放款失败退回 → UNAVAILABLE 交由人工处理
                return PAID.equals(to) || UNAVAILABLE.equals(to);
            default:
                // 全部终态不可再流转
                return false;
        }
    }

    /**
     * 用户可否自助取消（REQ-03）。
     * 仅待上门阶段允许，且此阶段取消不产生任何费用（取件未发生）。
     */
    public static boolean canUserCancel(String status) {
        return PENDING_PICKUP.equals(status);
    }

    /** 可否录入质检结果 / 调价（REQ-04）。 */
    public static boolean canQuote(String status) {
        return INSPECTING.equals(status) || PICKED_UP.equals(status);
    }

    /** 用户对报价做出接受/拒绝决策（REQ-04/REQ-05）。 */
    public static boolean canRespondQuote(String status) {
        return QUOTED.equals(status);
    }

    /** 可否放款（REQ-07）：仅用户已确认报价且尚未放款。 */
    public static boolean canPay(String status) {
        return CONFIRMED.equals(status);
    }

    /** 放款后可否开票/归档：放款成功方可形成对账凭证（REQ-07）。 */
    public static boolean isPayableFinal(String status) {
        return PAID.equals(status);
    }
}
