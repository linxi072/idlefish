package com.idlefish.trade.local;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.util.IdGenerator;
import com.idlefish.trade.item.entity.Item;
import com.idlefish.trade.item.mapper.ItemMapper;
import com.idlefish.trade.local.dto.RecycleApplyRequest;
import com.idlefish.trade.local.entity.RecycleOrder;
import com.idlefish.trade.local.mapper.RecycleOrderMapper;
import com.idlefish.trade.local.service.RecycleService;
import com.idlefish.trade.notify.enums.NotificationType;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.publish.ReferencePriceProvider;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回收服务单测（ITER-F05 / REQ-02~REQ-05、REQ-07、REQ-09、REQ-11）。
 * 覆盖：预估价三路来源、白名单/城市拒绝、无费用取消、报价封顶、拒绝不得强制成交、
 * 放款幂等与资金流水、PII 脱敏落库。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecycleServiceTest {

    @Mock
    private RecycleOrderMapper recycleOrderMapper;
    @Mock
    private ItemMapper itemMapper;
    @Mock
    private FundFlowMapper fundFlowMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ReferencePriceProvider referencePriceProvider;

    private IdlefishProperties props;
    private RecycleService service;

    @BeforeEach
    void setUp() {
        props = new IdlefishProperties();
        props.getLocal().setEnabled(true);
        props.getLocal().setCities(List.of("杭州"));
        props.getLocal().setRecycleCategories(List.of(100L, 200L));
        props.getLocal().setMinEstimateFen(100L);
        props.getLocal().setMaxQuoteDropRatio(0.5);
        props.getLocal().setAlertAdminUserId(9L);
        service = new RecycleService(recycleOrderMapper, itemMapper, fundFlowMapper, props,
                new MetricsRegistry(), notificationService, Optional.of(referencePriceProvider));
    }

    private RecycleApplyRequest req() {
        RecycleApplyRequest r = new RecycleApplyRequest();
        r.setCategoryId(100L);
        r.setConditionLevel(3);
        r.setCity("杭州");
        r.setAddress("浙江省杭州市西湖区文一路xx小区3号楼502室");
        r.setContactPhone("13812341234");
        r.setPickupType(LocalFulfillmentRules.PICKUP_HOME);
        return r;
    }

    // ===== REQ-02 预估 =====

    @Test
    @DisplayName("REQ-02 显式参考价优先：100000 × 0.62 = 62000 分")
    void estimate_inputReferenceWins() {
        com.idlefish.trade.local.vo.RecycleEstimateVO v =
                service.estimate(100L, 3, 100_000L, "杭州", null);
        assertTrue(v.isHasData());
        assertEquals(62_000L, v.getEstimateFen());
        assertEquals("input", v.getSource());
    }

    @Test
    @DisplayName("REQ-02 无显式参考价时回落 ITER-F03 估价引擎基准价")
    void estimate_fallbackToPricingEngine() {
        when(referencePriceProvider.basePriceFen(100L, 3)).thenReturn(Optional.of(200_000L));
        com.idlefish.trade.local.vo.RecycleEstimateVO v = service.estimate(100L, 3, null, "杭州", null);
        assertEquals(124_000L, v.getEstimateFen());
        assertEquals("pricing", v.getSource());
    }

    @Test
    @DisplayName("REQ-02 无任何参考价时明确无数据，不编造价格")
    void estimate_noDataWhenNoReference() {
        when(referencePriceProvider.basePriceFen(100L, 3)).thenReturn(Optional.empty());
        com.idlefish.trade.local.vo.RecycleEstimateVO v = service.estimate(100L, 3, null, "杭州", null);
        assertFalse(v.isHasData());
        assertNotNull(v.getUnavailableReason());
    }

    @Test
    @DisplayName("REQ-11 品类不在白名单 → 明确拒绝")
    void estimate_categoryNotWhitelisted() {
        com.idlefish.trade.local.vo.RecycleEstimateVO v = service.estimate(999L, 3, 100_000L, "杭州", null);
        assertFalse(v.isRecyclable());
        assertEquals(Code.RECYCLE_CATEGORY_NOT_ALLOWED.getCode(), 30034);
    }

    @Test
    @DisplayName("REQ-09 城市未开通 → 明确不可用")
    void estimate_cityUnsupported() {
        com.idlefish.trade.local.vo.RecycleEstimateVO v = service.estimate(100L, 3, 100_000L, "北京", null);
        assertFalse(v.isRecyclable());
        assertNotNull(v.getUnavailableReason());
    }

    @Test
    @DisplayName("REQ-09 总开关关闭 → 明确不可用")
    void estimate_disabledSwitch() {
        props.getLocal().setEnabled(false);
        com.idlefish.trade.local.vo.RecycleEstimateVO v = service.estimate(100L, 3, 100_000L, "杭州", null);
        assertFalse(v.isRecyclable());
    }

    // ===== REQ-02 申请 =====

    @Test
    @DisplayName("REQ-02 申请成功：预估价落库、地址与电话脱敏、状态待上门")
    void apply_successMasksPii() {
        when(referencePriceProvider.basePriceFen(100L, 3)).thenReturn(Optional.of(100_000L));
        service.apply(7L, req());

        ArgumentCaptor<RecycleOrder> cap = ArgumentCaptor.forClass(RecycleOrder.class);
        verify(recycleOrderMapper).insert(cap.capture());
        RecycleOrder r = cap.getValue();
        assertEquals(62_000L, r.getEstimateAmount());
        assertEquals(RecycleStateMachine.PENDING_PICKUP, r.getStatus());
        // PRD §九：地址隐去门牌号，电话仅保留前 3 后 4
        assertEquals("浙江省杭州市西湖区文一路xx小区3号楼", r.getAddress());
        assertEquals("138****1234", r.getContactPhone());
        assertNotNull(r.getRecycleNo());
        verify(notificationService).notify(anyLong(), any(NotificationType.class),
                any(), any(), any());
    }

    @Test
    @DisplayName("REQ-02 无参考价时拒绝发单（明确失败，不产生无定价黑盒单）")
    void apply_rejectedWhenNoReference() {
        when(referencePriceProvider.basePriceFen(100L, 3)).thenReturn(Optional.empty());
        BizException ex = assertThrows(BizException.class, () -> service.apply(7L, req()));
        assertEquals(Code.BIZ_ERROR.getCode(), ex.getCode());
        verify(recycleOrderMapper, never()).insert(any(RecycleOrder.class));
    }

    @Test
    @DisplayName("REQ-11 一键回收：以商品为准补齐类目/成色/标题快照")
    void apply_oneClickRecycleFromItem() {
        Item item = new Item();
        item.setId(5L);
        item.setCategoryId(200L);
        item.setConditionLevel(1);
        item.setTitle("iPhone 15 Pro");
        when(itemMapper.selectById(5L)).thenReturn(item);

        RecycleApplyRequest r = req();
        r.setItemId(5L);
        r.setCategoryId(null);
        r.setConditionLevel(null);
        r.setReferencePriceFen(100_000L);

        service.apply(7L, r);

        ArgumentCaptor<RecycleOrder> cap = ArgumentCaptor.forClass(RecycleOrder.class);
        verify(recycleOrderMapper).insert(cap.capture());
        RecycleOrder saved = cap.getValue();
        assertEquals(200L, saved.getCategoryId());
        assertEquals(1, saved.getConditionLevel());
        assertEquals("iPhone 15 Pro", saved.getTitle());
        // 全新系数 0.85
        assertEquals(85_000L, saved.getEstimateAmount());
    }

    @Test
    @DisplayName("REQ-11 关联商品不存在 → ITEM_NOT_FOUND")
    void apply_itemMissing() {
        when(itemMapper.selectById(5L)).thenReturn(null);
        RecycleApplyRequest r = req();
        r.setItemId(5L);
        BizException ex = assertThrows(BizException.class, () -> service.apply(7L, r));
        assertEquals(Code.ITEM_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("REQ-09 城市未开通时申请被拒")
    void apply_cityUnsupportedThrows() {
        RecycleApplyRequest r = req();
        r.setCity("北京");
        BizException ex = assertThrows(BizException.class, () -> service.apply(7L, r));
        assertEquals(Code.LOCAL_REGION_UNSUPPORTED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("PRD §九 脱敏函数独立可用：号码与地址不泄露明文")
    void privacyMasking_helpers() {
        assertEquals("138****1234", LocalFulfillmentRules.maskPhone("13812341234"));
        // 房间号剥离，楼栋保留
        assertEquals("xx小区3号楼", LocalFulfillmentRules.maskAddress("xx小区3号楼502室"));
        assertFalse(LocalFulfillmentRules.maskAddress("xx小区3号楼502室").contains("502"));
    }

    // ===== REQ-03 取消 =====

    @Test
    @DisplayName("REQ-03 待上门阶段取消成功，不产生费用")
    void cancel_beforePickupNoFee() {
        RecycleOrder r = recycle(RecycleStateMachine.PENDING_PICKUP);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);

        service.cancel(7L, 1L, "不想卖了");

        ArgumentCaptor<RecycleOrder> cap = ArgumentCaptor.forClass(RecycleOrder.class);
        verify(recycleOrderMapper).updateById(cap.capture());
        assertEquals(RecycleStateMachine.CANCELED, cap.getValue().getStatus());
        // 全程不碰资金流水
        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    @DisplayName("REQ-03 已揽收后禁止取消（引导走报价拒绝）")
    void cancel_afterPickupBlocked() {
        RecycleOrder r = recycle(RecycleStateMachine.PICKED_UP);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);
        BizException ex = assertThrows(BizException.class, () -> service.cancel(7L, 1L, "x"));
        assertEquals(Code.RECYCLE_STATE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("越权取消他人回收单 → FORBIDDEN")
    void cancel_notOwner() {
        RecycleOrder r = recycle(RecycleStateMachine.PENDING_PICKUP);
        r.setUserId(99L);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);
        BizException ex = assertThrows(BizException.class, () -> service.cancel(7L, 1L, "x"));
        assertEquals(Code.FORBIDDEN.getCode(), ex.getCode());
    }

    // ===== REQ-04 质检报价 =====

    @Test
    @DisplayName("REQ-04 质检报价服务端封顶并进入待确认")
    void quote_cappedAndPending() {
        RecycleOrder r = recycle(RecycleStateMachine.INSPECTING);
        r.setEstimateAmount(100_000L);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);

        service.quote(1L, 150_000L, "屏幕轻微划痕", "ops");

        ArgumentCaptor<RecycleOrder> cap = ArgumentCaptor.forClass(RecycleOrder.class);
        verify(recycleOrderMapper).updateById(cap.capture());
        RecycleOrder upd = cap.getValue();
        // 100000 × 1.2 = 120000（上限保护）
        assertEquals(120_000L, upd.getFinalAmount());
        assertEquals(RecycleStateMachine.QUOTED, upd.getStatus());
        assertEquals("屏幕轻微划痕", upd.getQuoteRemark());
    }

    @Test
    @DisplayName("REQ-04 跌幅超阈值触发异常调价告警，但不阻断流程（决策权归用户）")
    void quote_abnormalDropAlerts() {
        RecycleOrder r = recycle(RecycleStateMachine.INSPECTING);
        r.setEstimateAmount(100_000L);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);

        service.quote(1L, 30_000L, "严重进水", "ops");

        verify(notificationService).notify(org.mockito.ArgumentMatchers.eq(9L),
                any(NotificationType.class), any(), any(), any());
        ArgumentCaptor<RecycleOrder> cap = ArgumentCaptor.forClass(RecycleOrder.class);
        verify(recycleOrderMapper).updateById(cap.capture());
        assertEquals(30_000L, cap.getValue().getFinalAmount());
    }

    @Test
    @DisplayName("REQ-04 报价为非正数时拒绝")
    void quote_invalidAmount() {
        RecycleOrder r = recycle(RecycleStateMachine.INSPECTING);
        r.setEstimateAmount(100_000L);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);
        BizException ex = assertThrows(BizException.class, () -> service.quote(1L, 0L, "x", "ops"));
        assertEquals(Code.PARAM_INVALID.getCode(), ex.getCode());
    }

    // ===== REQ-05 用户表态 =====

    @Test
    @DisplayName("REQ-05 拒绝报价 → REJECTED 且不写任何资金流水（不得强制成交/扣费）")
    void respond_rejectNoFundFlow() {
        RecycleOrder r = recycle(RecycleStateMachine.QUOTED);
        r.setFinalAmount(50_000L);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);

        service.respond(7L, 1L, false);

        ArgumentCaptor<RecycleOrder> cap = ArgumentCaptor.forClass(RecycleOrder.class);
        verify(recycleOrderMapper).updateById(cap.capture());
        assertEquals(RecycleStateMachine.REJECTED, cap.getValue().getStatus());
        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    @DisplayName("REQ-04 接受报价 → CONFIRMED（唯一可放款前置）")
    void respond_acceptConfirmed() {
        RecycleOrder r = recycle(RecycleStateMachine.QUOTED);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);
        service.respond(7L, 1L, true);
        ArgumentCaptor<RecycleOrder> cap = ArgumentCaptor.forClass(RecycleOrder.class);
        verify(recycleOrderMapper).updateById(cap.capture());
        assertEquals(RecycleStateMachine.CONFIRMED, cap.getValue().getStatus());
    }

    @Test
    @DisplayName("REQ-05 已确认/已放款后不得再表态（防重复确认改价）")
    void respond_afterConfirmedBlocked() {
        RecycleOrder r = recycle(RecycleStateMachine.CONFIRMED);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);
        BizException ex = assertThrows(BizException.class, () -> service.respond(7L, 1L, true));
        assertEquals(Code.RECYCLE_STATE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    // ===== REQ-07 放款 =====

    @Test
    @DisplayName("REQ-07 放款：入 IN/RECYCLE 资金流水，金额取封顶后的最终价")
    void pay_writesFundFlow() {
        RecycleOrder r = recycle(RecycleStateMachine.CONFIRMED);
        r.setFinalAmount(50_000L);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);
        when(fundFlowMapper.selectCount(any())).thenReturn(0L);

        service.pay(1L, "ops");

        ArgumentCaptor<FundFlow> cap = ArgumentCaptor.forClass(FundFlow.class);
        verify(fundFlowMapper).insert(cap.capture());
        FundFlow ff = cap.getValue();
        assertEquals(50_000L, ff.getAmount());
        assertEquals("IN", ff.getDirection());
        assertEquals(RecycleService.RECYCLE_FLOW_TYPE, ff.getType());
        assertEquals(r.getRecycleNo(), ff.getBizNo());
        ArgumentCaptor<RecycleOrder> upd = ArgumentCaptor.forClass(RecycleOrder.class);
        verify(recycleOrderMapper).updateById(upd.capture());
        assertEquals(RecycleStateMachine.PAID, upd.getValue().getStatus());
    }

    @Test
    @DisplayName("REQ-07 幂等：已存在 RECYCLE 流水时拒绝重复放款")
    void pay_idempotent() {
        RecycleOrder r = recycle(RecycleStateMachine.CONFIRMED);
        r.setFinalAmount(50_000L);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);
        when(fundFlowMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.pay(1L, "ops"));
        assertEquals(Code.RECYCLE_STATE_NOT_ALLOWED.getCode(), ex.getCode());
        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    @DisplayName("REQ-07 未确认报价的单不可放款（系统不自动放款）")
    void pay_requiresConfirmation() {
        RecycleOrder r = recycle(RecycleStateMachine.QUOTED);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);
        BizException ex = assertThrows(BizException.class, () -> service.pay(1L, "ops"));
        assertEquals(Code.RECYCLE_STATE_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("REQ-07 金额非正时禁止放款")
    void pay_zeroAmountBlocked() {
        RecycleOrder r = recycle(RecycleStateMachine.CONFIRMED);
        r.setFinalAmount(0L);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);
        BizException ex = assertThrows(BizException.class, () -> service.pay(1L, "ops"));
        assertEquals(Code.PARAM_INVALID.getCode(), ex.getCode());
    }

    // ===== REQ-09 不可用 =====

    @Test
    @DisplayName("REQ-09 不可用置态：终态不重复处理，非终态置 UNAVAILABLE 并告知用户")
    void markUnavailable() {
        RecycleOrder r = recycle(RecycleStateMachine.PENDING_PICKUP);
        when(recycleOrderMapper.selectById(1L)).thenReturn(r);
        service.markUnavailable(1L, "服务商运力不足");
        ArgumentCaptor<RecycleOrder> cap = ArgumentCaptor.forClass(RecycleOrder.class);
        verify(recycleOrderMapper).updateById(cap.capture());
        assertEquals(RecycleStateMachine.UNAVAILABLE, cap.getValue().getStatus());
        assertEquals("服务商运力不足", cap.getValue().getCancelReason());

        RecycleOrder done = recycle(RecycleStateMachine.PAID);
        when(recycleOrderMapper.selectById(2L)).thenReturn(done);
        service.markUnavailable(2L, "x");
        verify(recycleOrderMapper, times(1)).updateById(any(RecycleOrder.class));
    }

    // ===== 查询 =====

    @Test
    @DisplayName("回收单不存在 → RECYCLE_NOT_FOUND")
    void notFound() {
        when(recycleOrderMapper.selectById(1L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.detail(7L, 1L));
        assertEquals(Code.RECYCLE_NOT_FOUND.getCode(), ex.getCode());
    }

    // ===== 辅助 =====

    private RecycleOrder recycle(String status) {
        RecycleOrder r = new RecycleOrder();
        r.setId(1L);
        r.setUserId(7L);
        r.setRecycleNo(IdGenerator.recycleNo());
        r.setStatus(status);
        r.setEstimateAmount(100_000L);
        return r;
    }
}
