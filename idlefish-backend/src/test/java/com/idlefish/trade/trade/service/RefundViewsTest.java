package com.idlefish.trade.trade.service;

import com.idlefish.trade.trade.entity.Refund;
import com.idlefish.trade.trade.vo.RefundVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 退款视图映射单测（纯函数）：Refund → RefundVO，含 createdAt 格式化与 null 安全。无 Mock、无 Spring。
 */
class RefundViewsTest {

    @Test
    void toVO_mapsAllFields() {
        Refund r = new Refund();
        r.setRefundNo("RF001");
        r.setOrderNo("ORD001");
        r.setBuyerId(1L);
        r.setSellerId(2L);
        r.setType("only_refund");
        r.setAmount(10000L);
        r.setReason("不想要了");
        r.setStatus("wait_seller");
        r.setLogisticsNo("SF123");
        r.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0, 0));
        RefundVO vo = RefundViews.toVO(r);
        assertEquals("RF001", vo.getRefundNo());
        assertEquals("ORD001", vo.getOrderNo());
        assertEquals(1L, vo.getBuyerId());
        assertEquals(2L, vo.getSellerId());
        assertEquals("only_refund", vo.getType());
        assertEquals(10000L, vo.getAmount());
        assertEquals("不想要了", vo.getReason());
        assertEquals("wait_seller", vo.getStatus());
        assertEquals("SF123", vo.getLogisticsNo());
        assertEquals("2026-01-01 12:00:00", vo.getCreatedAt());
    }

    @Test
    void toVO_nullEntityReturnsNull() {
        assertNull(RefundViews.toVO(null));
    }

    @Test
    void toVO_nullCreatedAtReturnsNull() {
        Refund r = new Refund();
        r.setRefundNo("RF002");
        r.setCreatedAt(null);
        RefundVO vo = RefundViews.toVO(r);
        assertNotNull(vo);
        assertNull(vo.getCreatedAt());
    }

    @Test
    void toVOList_mapsEachAndHandlesNullInput() {
        Refund a = new Refund();
        a.setRefundNo("RF_A");
        a.setCreatedAt(LocalDateTime.of(2026, 2, 2, 8, 30, 0));
        Refund b = new Refund();
        b.setRefundNo("RF_B");
        b.setCreatedAt(null);
        List<RefundVO> vos = RefundViews.toVOList(List.of(a, b));
        assertEquals(2, vos.size());
        assertEquals("RF_A", vos.get(0).getRefundNo());
        assertEquals("2026-02-02 08:30:00", vos.get(0).getCreatedAt());
        assertEquals("RF_B", vos.get(1).getRefundNo());
        assertEquals(0, RefundViews.toVOList(null).size());
        assertEquals(0, RefundViews.toVOList(List.of()).size());
    }
}
