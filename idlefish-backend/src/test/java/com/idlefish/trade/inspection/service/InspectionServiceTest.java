package com.idlefish.trade.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.inspection.InspectionStateMachine;
import com.idlefish.trade.inspection.dto.InspectionResultDTO;
import com.idlefish.trade.inspection.entity.InspectionOrder;
import com.idlefish.trade.inspection.entity.InspectionReport;
import com.idlefish.trade.inspection.mapper.InspectionOrderMapper;
import com.idlefish.trade.inspection.mapper.InspectionReportMapper;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.dto.RefundApplyDTO;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.service.RefundService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 鉴定验货服务单测（F-02）：聚焦状态推进、报告落库、自动退款（REQ-05）、发货冻结（REQ-04）与异常安全（REQ-09）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionServiceTest {

    @Mock private InspectionOrderMapper inspectionOrderMapper;
    @Mock private InspectionReportMapper inspectionReportMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private ItemService itemService;
    @Mock private RefundService refundService;
    @Mock private FundFlowMapper fundFlowMapper;
    @Mock private NotificationService notificationService;
    @Mock private MetricsRegistry metrics;

    @InjectMocks
    private InspectionService service;

    private static final Long ID = 1L;
    private static final Long BUYER = 1L;
    private static final Long SELLER = 2L;
    private static final Long ITEM = 10L;
    private static final String ORDER_NO = "ORD1";
    private static final String INSPECTION_NO = "IV1";

    private Order paidOrder() {
        Order o = new Order();
        o.setOrderNo(ORDER_NO);
        o.setBuyerId(BUYER);
        o.setSellerId(SELLER);
        o.setItemId(ITEM);
        o.setPayAmount(100_00L); // 100 元
        o.setStatus(OrderStatus.PAID.getCode());
        return o;
    }

    private InspectionOrder inspectingOrder() {
        InspectionOrder io = new InspectionOrder();
        io.setId(ID);
        io.setInspectionNo(INSPECTION_NO);
        io.setOrderNo(ORDER_NO);
        io.setItemId(ITEM);
        io.setBuyerId(BUYER);
        io.setSellerId(SELLER);
        io.setAgencyId(9L);
        io.setStatus(InspectionStateMachine.INSPECTING);
        return io;
    }

    @Test
    void create_buildsOrderAndRecordsFee() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(paidOrder());
        when(inspectionOrderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(inspectionOrderMapper.insert((InspectionOrder) any())).thenReturn(1);

        service.create(BUYER, ORDER_NO, "STANDARD", 9L);

        ArgumentCaptor<InspectionOrder> cap = ArgumentCaptor.forClass(InspectionOrder.class);
        verify(inspectionOrderMapper, times(1)).insert(cap.capture());
        InspectionOrder saved = cap.getValue();
        assertNotNull(saved.getInspectionNo());
        assertEquals(500L, saved.getFeeAmount()); // 100 元 * 2% = 200 分 < 最低 500 分，取下限
        // 服务费流水入账（REQ-10）
        verify(fundFlowMapper, times(1)).insert((FundFlow) any());
        verify(metrics, times(1)).increment("inspection.created");
    }

    @Test
    void create_rejectsNonOwner() {
        Order o = paidOrder();
        o.setBuyerId(999L);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(o);
        assertThrows(BizException.class, () -> service.create(BUYER, ORDER_NO, "STANDARD", 9L));
    }

    @Test
    void receiveResult_pass_marksInspectedAndStoresReport() {
        when(inspectionOrderMapper.selectById(ID)).thenReturn(inspectingOrder());
        when(inspectionReportMapper.insert(any(InspectionReport.class))).thenReturn(1);
        when(inspectionOrderMapper.updateById((InspectionOrder) any())).thenReturn(1);

        InspectionResultDTO dto = new InspectionResultDTO();
        dto.setPass(true);
        dto.setGrade("A");
        dto.setRawJson("{\"agency\":\"x\"}");

        service.receiveResult(ID, dto);

        verify(inspectionReportMapper, times(1)).insert((InspectionReport) any());
        verify(itemService, times(1)).markInspected(ITEM); // REQ-07 打「已验」标识
        verify(metrics, times(1)).increment("inspection.passed");
    }

    @Test
    void receiveResult_reject_triggersAutoRefund() {
        when(inspectionOrderMapper.selectById(ID)).thenReturn(inspectingOrder());
        when(inspectionReportMapper.insert(any(InspectionReport.class))).thenReturn(1);
        when(inspectionOrderMapper.updateById((InspectionOrder) any())).thenReturn(1);
        when(refundService.apply(eq(BUYER), any(RefundApplyDTO.class))).thenReturn("RF1");

        InspectionResultDTO dto = new InspectionResultDTO();
        dto.setPass(false);
        dto.setGrade("D");

        service.receiveResult(ID, dto);

        // REQ-05 不通过：复用既有退款链路（apply + 立即执行），不直接出款
        verify(refundService, times(1)).apply(eq(BUYER), any(RefundApplyDTO.class));
        verify(refundService, times(1)).adminAgree(ORDER_NO);
        verify(metrics, times(1)).increment("inspection.rejected");
    }

    @Test
    void cancel_blockedWhenInspecting() {
        InspectionOrder io = inspectingOrder(); // 验货中不可取消
        when(inspectionOrderMapper.selectById(ID)).thenReturn(io);
        assertThrows(BizException.class, () -> service.cancel(ID, BUYER));
        verify(inspectionOrderMapper, never()).updateById((InspectionOrder) any());
    }

    @Test
    void adminResolveException_onlyFromExceptionState() {
        InspectionOrder io = new InspectionOrder();
        io.setId(ID);
        io.setBuyerId(BUYER);
        io.setInspectionNo(INSPECTION_NO);
        io.setStatus(InspectionStateMachine.EXCEPTION);
        when(inspectionOrderMapper.selectById(ID)).thenReturn(io);
        when(inspectionOrderMapper.updateById((InspectionOrder) any())).thenReturn(1);

        service.adminResolveException(ID, "机构超时，平台释放");

        verify(inspectionOrderMapper, times(1)).updateById((InspectionOrder) any());
        verify(metrics, times(1)).increment("inspection.exception.resolved");
    }

    @Test
    void adminResolveException_rejectsNonExceptionState() {
        InspectionOrder io = inspectingOrder(); // 非 EXCEPTION
        when(inspectionOrderMapper.selectById(ID)).thenReturn(io);
        assertThrows(BizException.class, () -> service.adminResolveException(ID, "x"));
    }

    @Test
    void isShippingFrozen_reflectsOpenOrders() {
        when(inspectionOrderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertTrue(service.isShippingFrozen(ORDER_NO));
        when(inspectionOrderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        org.junit.jupiter.api.Assertions.assertFalse(service.isShippingFrozen(ORDER_NO));
    }
}
