package com.idlefish.trade.local.service;

import com.idlefish.trade.local.provider.TrackPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同城配送纯函数层单测（ITER-F05）。仅覆盖无副作用映射/计算，无需数据源。
 */
class LocalDeliveryViewsTest {

    @Test
    @DisplayName("isOrderBiz：大小写不敏感且非 ORDER 为假")
    void isOrderBiz() {
        assertTrue(LocalDeliveryViews.isOrderBiz("ORDER"));
        assertTrue(LocalDeliveryViews.isOrderBiz("order"));
        assertTrue(LocalDeliveryViews.isOrderBiz("Order"));
        assertEquals(false, LocalDeliveryViews.isOrderBiz("RECYCLE"));
        assertEquals(false, LocalDeliveryViews.isOrderBiz(null));
        assertEquals(false, LocalDeliveryViews.isOrderBiz(""));
    }

    @Test
    @DisplayName("toLogisticsStatus：状态映射 transport/signed/exception")
    void toLogisticsStatus() {
        assertEquals("transport", LocalDeliveryViews.toLogisticsStatus(null));
        assertEquals("transport", LocalDeliveryViews.toLogisticsStatus("CREATED"));
        assertEquals("transport", LocalDeliveryViews.toLogisticsStatus("DELIVERING"));
        assertEquals("signed", LocalDeliveryViews.toLogisticsStatus("SIGNED"));
        assertEquals("exception", LocalDeliveryViews.toLogisticsStatus("FAILED"));
        assertEquals("exception", LocalDeliveryViews.toLogisticsStatus("CANCELED"));
    }

    @Test
    @DisplayName("node：time 按 FMT 格式化且含 desc")
    void node() {
        Map<String, String> m = LocalDeliveryViews.node(LocalDateTime.of(2026, 10, 22, 9, 0, 0), "已创建");
        assertEquals("2026-10-22 09:00:00", m.get("time"));
        assertEquals("已创建", m.get("desc"));
        assertNotNull(m.get("time"));
    }

    @Test
    @DisplayName("toNodes：null 安全、空列表安全、字段归一化")
    void toNodes() {
        assertTrue(LocalDeliveryViews.toNodes(null).isEmpty());
        assertTrue(LocalDeliveryViews.toNodes(List.of()).isEmpty());

        TrackPoint p = new TrackPoint();
        p.setTime("2026-10-22 09:00:00");
        p.setDesc("骑手已接单");
        List<Map<String, String>> nodes = LocalDeliveryViews.toNodes(List.of(p));
        assertEquals(1, nodes.size());
        assertEquals("2026-10-22 09:00:00", nodes.get(0).get("time"));
        assertEquals("骑手已接单", nodes.get(0).get("desc"));
    }
}
