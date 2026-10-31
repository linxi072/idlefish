package com.idlefish.trade.marketing.activity.service;

import com.idlefish.trade.marketing.activity.entity.ActivityRecord;
import com.idlefish.trade.marketing.activity.entity.ActivityReconciliation;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.marketing.activity.vo.ActivityReconcileVO;
import com.idlefish.trade.marketing.activity.vo.MarketingActivityVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 活动内核纯函数单测：覆盖 VO 映射（toVO / toReconcileVO）与对账聚合（computeReconciliation）。
 * 无 Mapper / 缓存 / 指标依赖，纯逻辑零外部副作用。
 */
class ActivityViewsTest {

    private MarketingActivity activity() {
        MarketingActivity a = new MarketingActivity();
        a.setId(1L);
        a.setKind("LOTTERY");
        a.setTitle("测试活动");
        a.setStock(50);
        a.setSoldCount(10);
        a.setSubsidyBudgetFen(1000L);
        a.setSubsidyUsedFen(400L);
        a.setLimitPerUser(2);
        return a;
    }

    private ActivityRecord record(String status, long subsidyFen, Long amountFen) {
        ActivityRecord r = new ActivityRecord();
        r.setStatus(status);
        r.setSubsidyFen(subsidyFen);
        r.setAmountFen(amountFen);
        return r;
    }

    @Test
    void toVO_mapsAllFields_andJoinedFlag() {
        MarketingActivity a = activity();
        MarketingActivityVO vo = ActivityViews.toVO(a, true);
        assertEquals(1L, vo.getId());
        assertEquals("LOTTERY", vo.getKind());
        assertEquals("测试活动", vo.getTitle());
        assertEquals(50, vo.getStock());
        assertEquals(10, vo.getSoldCount());
        assertEquals(1000L, vo.getSubsidyBudgetFen());
        assertEquals(400L, vo.getSubsidyUsedFen());
        assertEquals(2, vo.getLimitPerUser());
        assertTrue(vo.getJoined());
    }

    @Test
    void toVO_joinedFalseWhenFlagFalse() {
        MarketingActivityVO vo = ActivityViews.toVO(activity(), false);
        assertFalse(vo.getJoined());
    }

    @Test
    void toReconcileVO_mapsReconFields() {
        MarketingActivity a = activity();
        ActivityReconciliation recon = new ActivityReconciliation();
        recon.setStockTotal(50);
        recon.setStockSold(5);
        recon.setStockRecycled(45);
        recon.setSubsidyTotalFen(200L);
        recon.setParticipantCount(5);
        recon.setDrawCount(5);
        recon.setWinCount(1);
        recon.setLossFen(150L);
        recon.setStatus("SETTLED");
        recon.setSettledAt(LocalDateTime.now());
        ActivityReconcileVO vo = ActivityViews.toReconcileVO(a, recon);
        assertEquals(1L, vo.getActivityId());
        assertEquals("LOTTERY", vo.getKind());
        assertEquals(50, vo.getStockTotal());
        assertEquals(5, vo.getStockSold());
        assertEquals(200L, vo.getSubsidyTotalFen());
        assertEquals(150L, vo.getLossFen());
        assertEquals("SETTLED", vo.getStatus());
    }

    @Test
    void toReconcileVO_nullRecon_onlyBase() {
        ActivityReconcileVO vo = ActivityViews.toReconcileVO(activity(), null);
        assertEquals(1L, vo.getActivityId());
        assertEquals("LOTTERY", vo.getKind());
        assertNull(vo.getStatus());
    }

    @Test
    void computeReconciliation_lottery_won() {
        MarketingActivity a = activity();
        List<ActivityRecord> records = new ArrayList<>();
        records.add(record("WON", 0L, 1000L));
        ActivityReconciliation recon = ActivityViews.computeReconciliation(a, records);
        assertEquals("LOTTERY", recon.getKind());
        assertEquals(50, recon.getStockTotal());
        assertEquals(1, recon.getStockSold());
        assertEquals(50, recon.getStockRecycled());
        assertEquals(0L, recon.getSubsidyTotalFen());
        assertEquals(1, recon.getParticipantCount());
        assertEquals(1, recon.getDrawCount());
        assertEquals(1, recon.getWinCount());
        // cost = lossFloor(0) * 1 = 0；loss = 0 + max(0, 0 - 1000) = 0
        assertEquals(0L, recon.getLossFen());
    }

    @Test
    void computeReconciliation_skipsCancelledAndLost() {
        MarketingActivity a = activity();
        List<ActivityRecord> records = new ArrayList<>();
        records.add(record("CANCELLED", 0L, 100L));
        records.add(record("LOST", 0L, 100L));
        records.add(record("WON", 0L, 500L));
        ActivityReconciliation recon = ActivityViews.computeReconciliation(a, records);
        assertEquals(1, recon.getStockSold()); // 仅 WON 计入
        assertEquals(3, recon.getParticipantCount());
        assertEquals(1, recon.getDrawCount());
        assertEquals(1, recon.getWinCount());
    }

    @Test
    void computeReconciliation_nonLottery_drawCountEqualsParticipant() {
        MarketingActivity a = activity();
        a.setKind("BARGAIN");
        List<ActivityRecord> records = new ArrayList<>();
        records.add(record("QUALIFIED", 0L, 800L));
        records.add(record("JOINED", 0L, null));
        ActivityReconciliation recon = ActivityViews.computeReconciliation(a, records);
        assertEquals(2, recon.getParticipantCount());
        assertEquals(2, recon.getDrawCount()); // 非抽奖：drawCount = max(drawCount, participant)
        assertEquals(0, recon.getWinCount());
        // revenue: QUALIFIED +800；JOINED amountFen null 跳过；cost=0 → loss=0
        assertEquals(0L, recon.getLossFen());
    }

    @Test
    void computeReconciliation_lossWithSubsidyAndCost() {
        MarketingActivity a = activity();
        a.setLossFloorFen(100L); // 单售成本 100
        a.setStock(0);           // 无回收、stockTotal 0
        List<ActivityRecord> records = new ArrayList<>();
        records.add(record("WON", 300L, 0L)); // 补贴 300、营收 0
        ActivityReconciliation recon = ActivityViews.computeReconciliation(a, records);
        assertEquals(1, recon.getStockSold());
        assertEquals(300L, recon.getSubsidyTotalFen());
        // cost = 100 * 1 = 100；loss = 300 + max(0, 100 - 0) = 400
        assertEquals(400L, recon.getLossFen());
        assertEquals(0, recon.getStockTotal());
        assertEquals(0, recon.getStockRecycled());
    }
}
