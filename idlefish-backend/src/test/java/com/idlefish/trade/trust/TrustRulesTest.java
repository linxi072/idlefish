package com.idlefish.trade.trust;

import com.idlefish.trade.dispute.DisputeStateMachine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 信任体系可视化纯函数规则单测（U02）：高客单判定、售后进度节点编排、保障边界。
 * 无 Spring 依赖，离线即可运行。
 */
class TrustRulesTest {

    @Test
    void isHighValue_thresholdAndNull() {
        assertFalse(TrustRules.isHighValue(null), "null 视为非高客单");
        assertFalse(TrustRules.isHighValue(99_999L), "99999 分（999.99 元）< 阈值");
        assertTrue(TrustRules.isHighValue(TrustRules.HIGH_VALUE_FEN), "恰好 100000 分（1000 元）视为高客单");
        assertTrue(TrustRules.isHighValue(200_000L), "200000 分 > 阈值");
    }

    @Test
    void buildDisputeProgress_nullReturnsEmpty() {
        assertTrue(TrustRules.buildDisputeProgress(null).isEmpty(), "null 状态返回空节点");
    }

    @Test
    void buildDisputeProgress_pending() {
        List<TrustRules.ProgressNode> nodes = TrustRules.buildDisputeProgress(DisputeStateMachine.PENDING);
        assertEquals(4, nodes.size());
        assertLabels(nodes);
        assertNode(nodes.get(0), DisputeStateMachine.PENDING, true, true);   // 当前：已申请
        assertNode(nodes.get(1), DisputeStateMachine.SELLER_REPLIED, false, false);
        assertNode(nodes.get(2), DisputeStateMachine.PLATFORM, false, false);
        assertNode(nodes.get(3), DisputeStateMachine.RESOLVED, false, false);
    }

    @Test
    void buildDisputeProgress_sellerReplied() {
        List<TrustRules.ProgressNode> nodes = TrustRules.buildDisputeProgress(DisputeStateMachine.SELLER_REPLIED);
        assertEquals(4, nodes.size());
        assertNode(nodes.get(0), DisputeStateMachine.PENDING, true, false);
        assertNode(nodes.get(1), DisputeStateMachine.SELLER_REPLIED, true, true); // 当前：举证中
        assertNode(nodes.get(2), DisputeStateMachine.PLATFORM, false, false);
        assertNode(nodes.get(3), DisputeStateMachine.RESOLVED, false, false);
    }

    @Test
    void buildDisputeProgress_platform() {
        List<TrustRules.ProgressNode> nodes = TrustRules.buildDisputeProgress(DisputeStateMachine.PLATFORM);
        assertEquals(4, nodes.size());
        assertNode(nodes.get(2), DisputeStateMachine.PLATFORM, true, true); // 当前：裁决中
        assertNode(nodes.get(3), DisputeStateMachine.RESOLVED, false, false);
    }

    @Test
    void buildDisputeProgress_resolvedAndClosed_terminal() {
        for (String s : new String[]{DisputeStateMachine.RESOLVED, DisputeStateMachine.CLOSED}) {
            List<TrustRules.ProgressNode> nodes = TrustRules.buildDisputeProgress(s);
            assertEquals(4, nodes.size());
            assertNode(nodes.get(3), DisputeStateMachine.RESOLVED, true, true); // 已完成（终态）
        }
    }

    @Test
    void buildDisputeProgress_canceled_singleTerminal() {
        List<TrustRules.ProgressNode> nodes = TrustRules.buildDisputeProgress(DisputeStateMachine.CANCELED);
        assertEquals(1, nodes.size());
        TrustRules.ProgressNode n = nodes.get(0);
        assertEquals(DisputeStateMachine.CANCELED, n.getStatus());
        assertEquals("已撤销", n.getLabel());
        assertTrue(n.isDone());
        assertTrue(n.isCurrent());
    }

    @Test
    void buildDisputeProgress_unknownFallsBackToPending() {
        List<TrustRules.ProgressNode> nodes = TrustRules.buildDisputeProgress("WEIRD");
        assertEquals(4, nodes.size());
        assertNode(nodes.get(0), DisputeStateMachine.PENDING, true, true); // 未知状态兜底为首节点
    }

    @Test
    void guaranteeApplicable_boundary() {
        assertFalse(TrustRules.guaranteeApplicable(true), "虚拟/违禁不适用担保");
        assertTrue(TrustRules.guaranteeApplicable(false), "实物适用担保");
    }

    private void assertLabels(List<TrustRules.ProgressNode> nodes) {
        assertEquals("已申请", nodes.get(0).getLabel());
        assertEquals("举证中", nodes.get(1).getLabel());
        assertEquals("裁决中", nodes.get(2).getLabel());
        assertEquals("已完成", nodes.get(3).getLabel());
    }

    private void assertNode(TrustRules.ProgressNode n, String status, boolean done, boolean current) {
        assertEquals(status, n.getStatus());
        assertEquals(done, n.isDone());
        assertEquals(current, n.isCurrent());
    }
}
