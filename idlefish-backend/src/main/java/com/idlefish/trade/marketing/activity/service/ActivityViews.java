package com.idlefish.trade.marketing.activity.service;

import com.idlefish.trade.marketing.activity.entity.ActivityReconciliation;
import com.idlefish.trade.marketing.activity.entity.ActivityRecord;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.marketing.activity.vo.ActivityReconcileVO;
import com.idlefish.trade.marketing.activity.vo.MarketingActivityVO;

import java.util.List;

/**
 * 活动内核纯函数层：VO 映射与对账聚合计算，无副作用、不持有任何 Mapper / 缓存 / 指标。
 * 拆分自 {@link ActivityKernelService}（ITER-F08），与 CouponViews / PricingViews / RefundViews 同范式。
 */
public final class ActivityViews {

    private ActivityViews() {
    }

    /** 实体 → VO（joined 由调用方基于参与记录判定后传入，避免本类依赖数据访问）。 */
    public static MarketingActivityVO toVO(MarketingActivity a, boolean joined) {
        MarketingActivityVO vo = new MarketingActivityVO();
        vo.setId(a.getId());
        vo.setKind(a.getKind());
        vo.setTitle(a.getTitle());
        vo.setItemId(a.getItemId());
        vo.setActivityPrice(a.getActivityPrice());
        vo.setStock(a.getStock());
        vo.setSoldCount(a.getSoldCount());
        vo.setLimitPerUser(a.getLimitPerUser());
        vo.setStatus(a.getStatus());
        vo.setStartAt(a.getStartAt());
        vo.setEndAt(a.getEndAt());
        vo.setAudienceType(a.getAudienceType());
        vo.setRiskLevel(a.getRiskLevel());
        vo.setSubsidyBudgetFen(a.getSubsidyBudgetFen());
        vo.setSubsidyUsedFen(a.getSubsidyUsedFen());
        vo.setPlayConfig(a.getPlayConfig());
        vo.setJoined(joined);
        return vo;
    }

    /** 对账实体 + 活动 → 列表 VO，recon 为 null 时仅填充活动基础信息。 */
    public static ActivityReconcileVO toReconcileVO(MarketingActivity a, ActivityReconciliation recon) {
        ActivityReconcileVO vo = new ActivityReconcileVO();
        vo.setActivityId(a.getId());
        vo.setKind(a.getKind());
        vo.setTitle(a.getTitle());
        if (recon != null) {
            vo.setStockTotal(recon.getStockTotal());
            vo.setStockSold(recon.getStockSold());
            vo.setStockRecycled(recon.getStockRecycled());
            vo.setSubsidyTotalFen(recon.getSubsidyTotalFen());
            vo.setParticipantCount(recon.getParticipantCount());
            vo.setDrawCount(recon.getDrawCount());
            vo.setWinCount(recon.getWinCount());
            vo.setLossFen(recon.getLossFen());
            vo.setStatus(recon.getStatus());
            vo.setSettledAt(recon.getSettledAt());
        }
        return vo;
    }

    /**
     * 对账聚合（REQ-11、REQ-15）：纯计算，依据活动与参与记录核算售出、补贴、亏损等。
     * 返回已填充派生数值字段（stockTotal/stockSold/stockRecycled/subsidyTotalFen/participantCount/
     * drawCount/winCount/lossFen）与 kind 的对账实体；状态与时间戳由调用方落库前补全。
     */
    public static ActivityReconciliation computeReconciliation(MarketingActivity a, List<ActivityRecord> records) {
        int stockSold = 0;
        long subsidyTotal = 0L;
        int participant = records.size();
        int drawCount = 0;
        int winCount = 0;
        long revenue = 0L;
        for (ActivityRecord r : records) {
            if ("CANCELLED".equals(r.getStatus()) || "LOST".equals(r.getStatus())) {
                continue;
            }
            stockSold++;
            if (r.getSubsidyFen() != null) {
                subsidyTotal += r.getSubsidyFen();
            }
            if ("LOTTERY".equals(a.getKind())) {
                drawCount++;
                if ("WON".equals(r.getStatus())) {
                    winCount++;
                }
            } else {
                drawCount = Math.max(drawCount, participant);
            }
            if ("WON".equals(r.getStatus()) || "QUALIFIED".equals(r.getStatus())
                    || "SETTLED".equals(r.getStatus())) {
                if (r.getAmountFen() != null) {
                    revenue += r.getAmountFen();
                }
            }
        }
        int stock = a.getStock() == null ? 0 : a.getStock();
        int recycled = a.getStock() == null ? 0 : Math.max(0, a.getStock());
        long costTotal = (a.getLossFloorFen() == null ? 0 : a.getLossFloorFen()) * stockSold;
        long lossFen = subsidyTotal + Math.max(0L, costTotal - revenue);

        ActivityReconciliation recon = new ActivityReconciliation();
        recon.setKind(a.getKind());
        recon.setStockTotal(stock);
        recon.setStockSold(stockSold);
        recon.setStockRecycled(recycled);
        recon.setSubsidyTotalFen(subsidyTotal);
        recon.setParticipantCount(participant);
        recon.setDrawCount(drawCount);
        recon.setWinCount(winCount);
        recon.setLossFen(lossFen);
        return recon;
    }
}
