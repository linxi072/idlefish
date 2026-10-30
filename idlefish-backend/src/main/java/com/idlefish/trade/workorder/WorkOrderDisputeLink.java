package com.idlefish.trade.workorder;

import com.idlefish.trade.dispute.DisputeStateMachine;
import com.idlefish.trade.dispute.service.DisputeService;
import com.idlefish.trade.workorder.service.WorkOrderService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 维权纠纷 → 客服工单的联通映射（F-12 补完）：无副作用纯函数，便于离线单测。
 *
 * <p>定位：维权单（{@code t_dispute}）承载资金裁决，客服工单（{@code t_work_order}）承载人工服务流转。
 * 当维权双方对裁决流程有疑问、或平台介入后仍需人工协调时，可「转人工」生成客服工单，
 * 两者通过 {@code dispute_no} 单向关联（工单可空，不强制绑定），互不污染各自状态机。
 */
public final class WorkOrderDisputeLink {

    /** 争议金额（分）优先级分档阈值。 */
    public static final long AMOUNT_URGENT_FEN = 100_000L;   // ≥1000 元
    public static final long AMOUNT_HIGH_FEN = 20_000L;      // ≥200 元
    public static final long AMOUNT_NORMAL_FEN = 5_000L;     // ≥50 元

    private static final Map<String, String> TYPE_MAPPING = buildTypeMapping();

    private WorkOrderDisputeLink() {
    }

    private static Map<String, String> buildTypeMapping() {
        Map<String, String> m = new HashMap<>();
        // 退款类争议 → 工单类型 REFUND
        m.put(DisputeService.TYPE_REFUND_REJECTED, WorkOrderService.TYPE_REFUND);
        m.put(DisputeService.TYPE_NOT_RECEIVED, WorkOrderService.TYPE_REFUND);
        m.put(DisputeService.TYPE_DAMAGED, WorkOrderService.TYPE_REFUND);
        // 描述不符 → 归为投诉（需人工协调，非纯退款流程）
        m.put(DisputeService.TYPE_NOT_AS_DESC, WorkOrderService.TYPE_COMPLAINT);
        return Collections.unmodifiableMap(m);
    }

    /** 该维权单是否可转人工（仅未结维权单可转，已裁决/已撤销/已归档不再新建工单）。 */
    public static boolean canConvert(String disputeStatus) {
        return DisputeStateMachine.isOpen(disputeStatus);
    }

    /** 争议类型 → 工单类型；未知类型兜底 GENERAL。 */
    public static String typeOf(String disputeType) {
        String t = TYPE_MAPPING.get(disputeType);
        return t == null ? WorkOrderService.TYPE_GENERAL : t;
    }

    /**
     * 争议金额 + 维权状态 → 工单优先级。
     * 平台已介入（PLATFORM）至少 HIGH：说明双方未能自行达成一致，需优先人工跟进。
     */
    public static String priorityOf(Long amountFen, String disputeStatus) {
        long amount = amountFen == null ? 0L : amountFen;
        String base;
        if (amount >= AMOUNT_URGENT_FEN) {
            base = WorkOrderService.PRIORITY_URGENT;
        } else if (amount >= AMOUNT_HIGH_FEN) {
            base = WorkOrderService.PRIORITY_HIGH;
        } else if (amount >= AMOUNT_NORMAL_FEN) {
            base = WorkOrderService.PRIORITY_NORMAL;
        } else {
            base = WorkOrderService.PRIORITY_LOW;
        }
        if (DisputeStateMachine.PLATFORM.equals(disputeStatus)
                && !WorkOrderService.PRIORITY_URGENT.equals(base)
                && !WorkOrderService.PRIORITY_HIGH.equals(base)) {
            return WorkOrderService.PRIORITY_HIGH;
        }
        return base;
    }

    /** 转人工工单标题（含维权单号，便于客服与用户双向定位）。 */
    public static String subjectOf(String disputeNo, String disputeType) {
        String label;
        switch (disputeType == null ? "" : disputeType) {
            case DisputeService.TYPE_REFUND_REJECTED:
                label = "退款被拒";
                break;
            case DisputeService.TYPE_NOT_RECEIVED:
                label = "未收到货";
                break;
            case DisputeService.TYPE_DAMAGED:
                label = "商品破损";
                break;
            case DisputeService.TYPE_NOT_AS_DESC:
                label = "描述不符";
                break;
            default:
                label = "维权咨询";
                break;
        }
        return "维权转人工（" + label + "）：" + disputeNo;
    }

    /** 未填写描述时的默认问题描述（带上维权诉求，避免空描述）。 */
    public static String defaultDescription(String disputeType, String expectation) {
        String expect = DisputeService.EXPECT_RETURN_REFUND.equals(expectation) ? "退货退款" : "仅退款";
        return "用户就维权单发起人工客服协助，诉求：" + expect + "，争议类型：" + (disputeType == null ? "未分类" : disputeType);
    }
}
