package com.idlefish.trade.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.enums.RefundStatus;
import com.idlefish.trade.common.idempotent.IdempotentService;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.trade.entity.Refund;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.trade.mapper.RefundMapper;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.trade.service.PayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款服务单测（P0-2）：聚焦并发/集群下「重复退款」风险。
 * <p>
 * 通过内存态模拟数据库，对 {@code UPDATE ... WHERE status=wait_seller} 的 CAS 语义进行仿真：
 * 仅当当前状态确为 wait_seller 时「置 refunding」的更新才返回 affected=1，否则返回 0。
 * 由此可验证「手动同意」与「48h 自动同意」竞态下，真实出款（payService.refund）只执行一次。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundServiceTest {

    @Mock private RefundMapper refundMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private PayOrderMapper payOrderMapper;
    @Mock private PayService payService;
    @Mock private ItemService itemService;
    @Mock private FundFlowMapper fundFlowMapper;
    @Mock private NotificationService notificationService;
    @Mock private MetricsRegistry metrics;
    @Mock private IdempotentService idempotentService;

    /** 模拟数据库：refundId -> 当前状态。 */
    private final Map<Long, String> db = new ConcurrentHashMap<>();

    private static final Long REFUND_ID = 100L;
    private static final String REFUND_NO = "RF202601010001";
    private static final String ORDER_NO = "ORD202601010001";
    private static final String PAY_NO = "PAY202601010001";
    private static final Long BUYER = 1L;
    private static final Long SELLER = 2L;
    private static final Long AMOUNT = 100L;

    private RefundService service() {
        return new RefundService(refundMapper, orderMapper, payOrderMapper, payService,
                itemService, fundFlowMapper, notificationService, metrics, idempotentService);
    }

    /** 装配模拟 DB：selectOne 始终返回 WAIT_SELLER（建模并发竞态「两线程写入前均读到待处理态」的 TOCTOU 前置）；
     *  update 按 CAS 语义推进状态，仅当当前态匹配期望才返回 affected=1。 */
    private void wireMockDb() {
        when(refundMapper.selectOne(ArgumentMatchers.<LambdaQueryWrapper<Refund>>any())).thenAnswer(inv -> {
            Refund r = new Refund();
            r.setId(REFUND_ID);
            r.setRefundNo(REFUND_NO);
            r.setOrderNo(ORDER_NO);
            r.setPayNo(PAY_NO);
            r.setBuyerId(BUYER);
            r.setSellerId(SELLER);
            r.setAmount(AMOUNT);
            r.setType("only_refund");
            r.setStatus(RefundStatus.WAIT_SELLER.getCode());
            return r;
        });
        when(refundMapper.update(any(Refund.class), any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            Refund entity = inv.getArgument(0);
            String newStatus = entity.getStatus();
            String cur = db.get(REFUND_ID);
            if (RefundStatus.REFUNDING.getCode().equals(newStatus)) {
                // 置 refunding 的 CAS：仅当当前为 wait_seller 才成功（affected=1）
                if (RefundStatus.WAIT_SELLER.getCode().equals(cur)) {
                    db.put(REFUND_ID, RefundStatus.REFUNDING.getCode());
                    return 1;
                }
                return 0;
            }
            if (RefundStatus.REFUNDED.getCode().equals(newStatus)) {
                // 置 refunded 的 CAS：仅当当前为 refunding 才成功
                if (RefundStatus.REFUNDING.getCode().equals(cur)) {
                    db.put(REFUND_ID, RefundStatus.REFUNDED.getCode());
                    return 1;
                }
                return 0;
            }
            // 其它状态变更（拒绝/撤销等）直接套用
            if (newStatus != null) {
                db.put(REFUND_ID, newStatus);
                return 1;
            }
            return 0;
        });
    }

    @Test
    void agree_refunds_once_on_happy_path() {
        db.put(REFUND_ID, RefundStatus.WAIT_SELLER.getCode());
        wireMockDb();

        service().agree(SELLER, REFUND_NO);

        verify(payService, times(1)).refund(eq(PAY_NO), eq(AMOUNT));
        assertEquals(RefundStatus.REFUNDED.getCode(), db.get(REFUND_ID),
                "退款完成后状态应推进为 refunded");
    }

    /**
     * P0-2 核心回归：模拟「手动同意」与「48h 自动同意」竞态——
     * 两次 agree 都先读到 wait_seller（selectOne 始终返回 wait_seller），
     * 但仅首个线程的 CAS 推进成功，第二个线程 affected=0 被拦截，真实出款只执行一次。
     * 若该 CAS 缺口未修复，payService.refund 将被调用两次（双倍退款）。
     */
    @Test
    void concurrent_agree_refunds_only_once() {
        db.put(REFUND_ID, RefundStatus.WAIT_SELLER.getCode());
        wireMockDb();

        service().agree(SELLER, REFUND_NO);
        service().agree(SELLER, REFUND_NO);

        verify(payService, times(1)).refund(any(), any());
        assertEquals(RefundStatus.REFUNDED.getCode(), db.get(REFUND_ID));
    }
}
