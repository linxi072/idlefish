package com.idlefish.trade.trust.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.enums.CreditLevel;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.dispute.entity.Dispute;
import com.idlefish.trade.dispute.mapper.DisputeMapper;
import com.idlefish.trade.inspection.entity.InspectionReport;
import com.idlefish.trade.inspection.mapper.InspectionReportMapper;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.risk.entity.RiskEvent;
import com.idlefish.trade.risk.mapper.RiskEventMapper;
import com.idlefish.trade.trade.entity.Logistics;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.mapper.LogisticsMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.service.LogisticService;
import com.idlefish.trade.trade.vo.OrderLogisticsVO;
import com.idlefish.trade.trust.vo.TrustItemView;
import com.idlefish.trade.trust.vo.TrustOrderView;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.service.CreditService;
import com.idlefish.trade.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 信任体系可视化聚合服务单测（U02）：聚焦信用汇总、验货标强绑定、风控限制下单、
 * 高客单建议验货、物流/售后聚合，以及降级（新卖家/未发货/无工单）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrustViewServiceTest {

    @Mock private ItemService itemService;
    @Mock private UserService userService;
    @Mock private CreditService creditService;
    @Mock private OrderMapper orderMapper;
    @Mock private RiskEventMapper riskEventMapper;
    @Mock private InspectionReportMapper inspectionReportMapper;
    @Mock private DisputeMapper disputeMapper;
    @Mock private LogisticsMapper logisticsMapper;
    @Mock private LogisticService logisticService;
    @Mock private MetricsRegistry metrics;

    @InjectMocks
    private TrustViewService service;

    private static final Long SELLER = 2L;
    private static final Long ITEM = 10L;

    private Item baseItem(String inspectionStatus, Long price) {
        Item item = new Item();
        item.setId(ITEM);
        item.setSellerId(SELLER);
        item.setPrice(price);
        item.setInspectionStatus(inspectionStatus);
        return item;
    }

    private User user(int creditScore, int status) {
        User u = new User();
        u.setCreditScore(creditScore);
        u.setStatus(status);
        return u;
    }

    @BeforeEach
    void stubCommon() {
        // 默认：卖家正常、信用良好、无风控、无成交（单测按需覆盖）
        when(userService.getById(anyLong())).thenReturn(user(100, 0));
        when(creditService.levelOf(anyLong())).thenReturn(CreditLevel.GOOD);
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        when(riskEventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
    }

    // ---------- 商品详情信任条 ----------

    @Test
    void itemView_normalSeller_fullView() {
        when(itemService.view(anyLong())).thenReturn(baseItem("NONE", 50_000L));

        TrustItemView v = service.itemView(ITEM);

        assertNotNull(v.getSellerCredit());
        assertEquals(100, v.getSellerCredit().getCreditScore());
        assertEquals("GOOD", v.getSellerCredit().getCreditLevel());
        assertEquals("良好", v.getSellerCredit().getCreditLevelLabel());
        assertEquals(5, v.getSellerCredit().getDealCount());
        assertFalse(v.getSellerCredit().isNewSeller());

        assertNotNull(v.getInspectionBadge());
        assertEquals("NONE", v.getInspectionBadge().getStatus());
        assertFalse(v.getInspectionBadge().isPassed());
        assertNull(v.getInspectionBadge().getReportNo());

        assertFalse(v.getRiskFlag().isBlocked());
        assertFalse(v.getRiskFlag().isUnderRiskControl());
        assertFalse(v.isOrderRestricted());

        assertFalse(v.getSuggestInspection().isSuggested(), "非高客单不提示验货");
        assertNotNull(v.getGuaranteeSummary());
        assertNotNull(v.getGuaranteeBoundary());
        verify(metrics, times(1)).increment("trust.item.view");
    }

    @Test
    void itemView_newSeller_zeroCredit_showsNewSeller() {
        // REQ-08：无信用分且零成交 → 新卖家，不得显高分
        when(userService.getById(anyLong())).thenReturn(user(0, 0));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(itemService.view(anyLong())).thenReturn(baseItem("NONE", 30_000L));

        TrustItemView v = service.itemView(ITEM);
        assertTrue(v.getSellerCredit().isNewSeller());
        assertEquals(0, v.getSellerCredit().getCreditScore());
        assertEquals(0, v.getSellerCredit().getDealCount());
    }

    @Test
    void itemView_sellerNotFound_fallsBackToNewSeller() {
        // 卖家不存在不阻断：按新卖家处理
        when(userService.getById(anyLong())).thenThrow(new BizException(Code.USER_NOT_FOUND));
        when(creditService.levelOf(anyLong())).thenReturn(CreditLevel.POOR);
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(itemService.view(anyLong())).thenReturn(baseItem("NONE", 30_000L));

        TrustItemView v = service.itemView(ITEM);
        assertTrue(v.getSellerCredit().isNewSeller());
    }

    @Test
    void itemView_inspectionPassed_withReport_bindsReportNo() {
        // REQ-02：验货通过且与报告强绑定
        when(itemService.view(anyLong())).thenReturn(baseItem("PASSED", 80_000L));
        InspectionReport r = new InspectionReport();
        r.setReportNo("R-123");
        r.setGrade("A");
        when(inspectionReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(r);

        TrustItemView v = service.itemView(ITEM);
        assertTrue(v.getInspectionBadge().isPassed());
        assertEquals("R-123", v.getInspectionBadge().getReportNo());
        assertEquals("A", v.getInspectionBadge().getGrade());
    }

    @Test
    void itemView_inspectionPassed_withoutReport_noReportNo() {
        // REQ-02：通过但报告缺失 → 不展示报告入口（强绑定）
        when(itemService.view(anyLong())).thenReturn(baseItem("PASSED", 80_000L));
        when(inspectionReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        TrustItemView v = service.itemView(ITEM);
        assertTrue(v.getInspectionBadge().isPassed());
        assertNull(v.getInspectionBadge().getReportNo(), "无报告快照不应带 reportNo");
    }

    @Test
    void itemView_blockedSeller_restrictsOrder() {
        // REQ-09：封禁账号 → 限制下单
        when(userService.getById(anyLong())).thenReturn(user(100, 1)); // status=1 封禁
        when(itemService.view(anyLong())).thenReturn(baseItem("NONE", 20_000L));

        TrustItemView v = service.itemView(ITEM);
        assertTrue(v.getRiskFlag().isBlocked());
        assertTrue(v.isOrderRestricted());
        assertEquals("该卖家账号已被封禁，交易存在风险", v.getRiskFlag().getTip());
    }

    @Test
    void itemView_underRiskControl_restrictsOrder() {
        // REQ-09：存在 high 级风控事件 → 限制下单
        when(riskEventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(itemService.view(anyLong())).thenReturn(baseItem("NONE", 20_000L));

        TrustItemView v = service.itemView(ITEM);
        assertFalse(v.getRiskFlag().isBlocked());
        assertTrue(v.getRiskFlag().isUnderRiskControl());
        assertTrue(v.isOrderRestricted());
        assertEquals("该卖家存在风控处置记录，请谨慎交易", v.getRiskFlag().getTip());
    }

    @Test
    void itemView_highValue_uninspected_suggestsInspection() {
        // REQ-10：高客单且未验货 → 建议验货
        when(itemService.view(anyLong())).thenReturn(baseItem("NONE", 150_000L));
        TrustItemView v = service.itemView(ITEM);
        assertTrue(v.getSuggestInspection().isSuggested());
        assertEquals("去验货", v.getSuggestInspection().getEntry());
        assertNotNull(v.getSuggestInspection().getReason());
    }

    @Test
    void itemView_highValue_butPassed_noSuggest() {
        // 已验货不重复建议
        when(itemService.view(anyLong())).thenReturn(baseItem("PASSED", 150_000L));
        when(inspectionReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        TrustItemView v = service.itemView(ITEM);
        assertFalse(v.getSuggestInspection().isSuggested());
    }

    @Test
    void itemView_itemNotFound_throws() {
        when(itemService.view(anyLong())).thenThrow(new BizException(Code.ITEM_NOT_FOUND));
        BizException ex = assertThrows(BizException.class, () -> service.itemView(ITEM));
        assertEquals(Code.ITEM_NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- 订单详情信任视图 ----------

    @Test
    void orderView_noLogistics_noDispute() {
        when(logisticsMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(disputeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        TrustOrderView v = service.orderView("ORD1");
        assertNull(v.getLogistics(), "未发货无物流时间轴");
        assertNotNull(v.getAftersale());
        assertFalse(v.getAftersale().isHasDispute());
        assertNotNull(v.getGuaranteeSummary());
        assertNotNull(v.getGuaranteeBoundary());
        verify(metrics, times(1)).increment("trust.order.view");
    }

    @Test
    void orderView_withLogisticsAndDispute() {
        Logistics l = new Logistics();
        l.setLogisticsNo("L1");
        l.setCompany("SF");
        l.setStatus("signed");
        when(logisticsMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(l);
        when(logisticService.track(anyString())).thenReturn(List.of(
                Map.of("time", "2026-10-01 10:00", "desc", "已签收，本人签收")));

        Dispute d = new Dispute();
        d.setOrderNo("ORD1");
        d.setStatus("PLATFORM");
        d.setResult("BUYER_WIN");
        when(disputeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(d));

        TrustOrderView v = service.orderView("ORD1");
        assertNotNull(v.getLogistics());
        assertEquals("顺丰速运", v.getLogistics().getCompanyName());
        assertEquals("已签收", v.getLogistics().getStatusText());

        assertTrue(v.getAftersale().isHasDispute());
        assertEquals("PLATFORM", v.getAftersale().getCurrentStatus());
        assertEquals("平台介入中", v.getAftersale().getCurrentStatusLabel());
        assertEquals("BUYER_WIN", v.getAftersale().getResult());
        assertEquals(4, v.getAftersale().getNodes().size());
    }

    // ---------- 保障条款 ----------

    @Test
    void guaranteeTerms_present() {
        var t = service.guaranteeTerms();
        assertNotNull(t.getVersion());
        assertFalse(t.getSteps().isEmpty());
        assertFalse(t.getExclusions().isEmpty());
        assertEquals("适用于平台实物二手商品交易（含验货商品）", t.getApplicableScope());
    }
}
