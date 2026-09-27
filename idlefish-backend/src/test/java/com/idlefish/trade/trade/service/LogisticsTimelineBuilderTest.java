package com.idlefish.trade.trade.service;

import com.idlefish.trade.trade.vo.LogisticsTrack;
import com.idlefish.trade.trade.vo.OrderLogisticsVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-14.5 纯函数单测：物流时间轴构造器（离线、无 Spring / 无 MySQL）。
 */
class LogisticsTimelineBuilderTest {

    @Test
    void companyName_mapsKnownCodes() {
        assertEquals("顺丰速运", LogisticsTimelineBuilder.companyName("SF"));
        assertEquals("顺丰速运", LogisticsTimelineBuilder.companyName("sf"));
        assertEquals("中通快递", LogisticsTimelineBuilder.companyName("ZTO"));
        assertEquals("京东物流", LogisticsTimelineBuilder.companyName("JD"));
    }

    @Test
    void companyName_fallback() {
        assertEquals("未知", LogisticsTimelineBuilder.companyName(null));
        assertEquals("未知", LogisticsTimelineBuilder.companyName(""));
        assertEquals("OTHER", LogisticsTimelineBuilder.companyName("OTHER"));
    }

    @Test
    void statusText_maps() {
        assertEquals("已签收", LogisticsTimelineBuilder.statusText("signed"));
        assertEquals("异常", LogisticsTimelineBuilder.statusText("exception"));
        assertEquals("运输中", LogisticsTimelineBuilder.statusText("transport"));
        assertEquals("运输中", LogisticsTimelineBuilder.statusText(null));
    }

    @Test
    void classify_nodeTypes() {
        assertEquals("sign", LogisticsTimelineBuilder.classify("您的快件已签收，签收人：本人"));
        assertEquals("sign", LogisticsTimelineBuilder.classify("快件已代收"));
        assertEquals("exception", LogisticsTimelineBuilder.classify("快件异常，已退回"));
        assertEquals("exception", LogisticsTimelineBuilder.classify("收件人拒收"));
        assertEquals("transit", LogisticsTimelineBuilder.classify("快件正在派送途中"));
        assertEquals("transit", LogisticsTimelineBuilder.classify("派件员已出发"));
        assertEquals("transport", LogisticsTimelineBuilder.classify("【SF123】已揽收"));
        assertEquals("transport", LogisticsTimelineBuilder.classify("快件已发出，发往下一站"));
        assertEquals("transport", LogisticsTimelineBuilder.classify("快件已到达【北京分拨中心】"));
        assertEquals("unknown", LogisticsTimelineBuilder.classify("系统已收到您的寄件请求"));
        assertEquals("unknown", LogisticsTimelineBuilder.classify(null));
    }

    @Test
    void build_assemblesStructuredTimeline() {
        List<Map<String, String>> nodes = new ArrayList<>();
        nodes.add(of("2026-09-20 09:00", "【SF123】已揽收"));          // 旧
        nodes.add(of("2026-09-20 12:00", "快件已到达【北京分拨中心】")); // 中
        nodes.add(of("2026-09-20 18:00", "您的快件已签收，签收人：本人")); // 新

        OrderLogisticsVO vo = LogisticsTimelineBuilder.build(nodes, "SF", "signed");

        assertEquals("SF", vo.getCompany());
        assertEquals("顺丰速运", vo.getCompanyName());
        assertEquals("signed", vo.getStatus());
        assertEquals("已签收", vo.getStatusText());
        assertNotNull(vo.getTracks());
        assertEquals(3, vo.getTracks().size());

        // 最新节点在前
        LogisticsTrack first = vo.getTracks().get(0);
        assertTrue(first.getDesc().contains("已签收"));
        assertEquals("sign", first.getType());

        LogisticsTrack last = vo.getTracks().get(2);
        assertTrue(last.getDesc().contains("已揽收"));
        assertEquals("transport", last.getType());
    }

    @Test
    void build_handlesEmptyAndNull() {
        OrderLogisticsVO empty = LogisticsTimelineBuilder.build(new ArrayList<>(), null, null);
        assertTrue(empty.getTracks().isEmpty());
        assertEquals("未知", empty.getCompanyName());
        assertEquals("运输中", empty.getStatusText());

        OrderLogisticsVO nil = LogisticsTimelineBuilder.build(null, "YTO", "transport");
        assertTrue(nil.getTracks().isEmpty());
        assertEquals("圆通速递", nil.getCompanyName());
        assertEquals("运输中", nil.getStatusText());
    }

    private static Map<String, String> of(String time, String desc) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("time", time);
        m.put("desc", desc);
        return m;
    }
}
