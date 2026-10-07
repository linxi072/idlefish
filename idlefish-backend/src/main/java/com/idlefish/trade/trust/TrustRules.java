package com.idlefish.trade.trust;

import com.idlefish.trade.dispute.DisputeStateMachine;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 信任体系可视化（U02）纯函数规则层：无副作用、不依赖 Spring / 数据库，便于离线单测。
 *
 * <p>职责：
 * <ul>
 *   <li>高客单判定（REQ-10）：售价 ≥ 1000 元（100000 分）视为高客单，提示建议验货；</li>
 *   <li>售后进度节点编排（REQ-05）：将 F-17 工单状态机映射为「已申请→举证中→裁决中→已完成」可视化节点；</li>
 *   <li>保障适用边界（REQ-11）：虚拟商品 / 违禁品不适用担保交易，由调用方传入标记。</li>
 * </ul>
 */
public final class TrustRules {

    /** 高客单阈值（分）：1000 元。 */
    public static final long HIGH_VALUE_FEN = 100_000L;

    /** 售后进度节点顺序（终端「已撤销」单独处理）。 */
    private static final String[] NODE_SEQUENCE = {
            DisputeStateMachine.PENDING,
            DisputeStateMachine.SELLER_REPLIED,
            DisputeStateMachine.PLATFORM,
            DisputeStateMachine.RESOLVED
    };

    private static final String[] NODE_LABELS = {"已申请", "举证中", "裁决中", "已完成"};

    private TrustRules() {
    }

    /** REQ-10 高客单判定。 */
    public static boolean isHighValue(Long priceFen) {
        return priceFen != null && priceFen >= HIGH_VALUE_FEN;
    }

    /**
     * REQ-05 售后进度节点编排：根据当前工单状态生成可视化节点列表（当前节点高亮、之前节点标记完成）。
     * 与 F-17 {@link DisputeStateMachine} 严格对齐，状态变更时节点自动推进。
     */
    public static List<ProgressNode> buildDisputeProgress(String status) {
        List<ProgressNode> nodes = new ArrayList<>();
        if (status == null) {
            return nodes;
        }
        // 已撤销：单独呈现终态节点
        if (DisputeStateMachine.CANCELED.equals(status)) {
            ProgressNode n = new ProgressNode();
            n.setStatus(DisputeStateMachine.CANCELED);
            n.setLabel("已撤销");
            n.setDone(true);
            n.setCurrent(true);
            nodes.add(n);
            return nodes;
        }
        int reachedIndex;
        if (DisputeStateMachine.PLATFORM.equals(status)) {
            reachedIndex = 2;
        } else if (DisputeStateMachine.SELLER_REPLIED.equals(status)) {
            reachedIndex = 1;
        } else if (DisputeStateMachine.RESOLVED.equals(status) || DisputeStateMachine.CLOSED.equals(status)) {
            reachedIndex = NODE_SEQUENCE.length - 1; // RESOLVED 节点
        } else {
            reachedIndex = 0; // PENDING 或未知兜底
        }
        for (int i = 0; i < NODE_SEQUENCE.length; i++) {
            ProgressNode n = new ProgressNode();
            n.setStatus(NODE_SEQUENCE[i]);
            n.setLabel(NODE_LABELS[i]);
            n.setDone(i <= reachedIndex);
            n.setCurrent(i == reachedIndex);
            nodes.add(n);
        }
        return nodes;
    }

    /** REQ-11 保障是否适用于该商品：虚拟商品 / 违禁品不适用担保交易。 */
    public static boolean guaranteeApplicable(boolean virtualOrForbidden) {
        return !virtualOrForbidden;
    }

    /** 售后进度可视化节点（纯数据，供 VO 承载）。 */
    @Data
    public static class ProgressNode {
        private String status;
        private String label;
        private boolean done;
        private boolean current;
    }
}
