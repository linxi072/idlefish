package com.idlefish.trade.marketing.activity;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.marketing.activity.entity.MarketingActivity;
import com.idlefish.trade.user.entity.User;

import java.time.LocalDateTime;

/**
 * 统一活动内核校验纯函数（ITER-F08 / REQ-01、REQ-12）。
 * <p>
 * 无副作用、无数据库 / 缓存依赖，离线可单测。覆盖：
 * <ul>
 *   <li>时间窗判定 {@link #assertOngoing}；</li>
 *   <li>库存充足性 {@link #assertStock} / {@link #canDeduct}；</li>
 *   <li>每人限领 {@link #assertUserLimit}；</li>
 *   <li>人群定向 {@link #assertAudience}（REQ-13）；</li>
 *   <li>亏损阻断 {@link #assertLossSafe}（REQ-14，配置阶段 / 参与前均可调用）。</li>
 * </ul>
 * 各玩法（砍价 / 抽奖 / 直播秒杀）必须复用本类，不得自行实现上述逻辑（REQ-01）。
 */
public final class ActivityCore {

    private ActivityCore() {
    }

    /** 活动必须处于进行中且在时间窗内（REQ-03）。 */
    public static void assertOngoing(MarketingActivity a, LocalDateTime now) {
        if (a == null) {
            throw new BizException(Code.ACTIVITY_NOT_FOUND);
        }
        if (!"ONGOING".equals(a.getStatus())) {
            throw new BizException(Code.ACTIVITY_NOT_ONGOING, "活动未开始或已结束");
        }
        if (a.getStartAt() != null && now.isBefore(a.getStartAt())) {
            throw new BizException(Code.ACTIVITY_NOT_ONGOING, "活动未开始");
        }
        if (a.getEndAt() != null && now.isAfter(a.getEndAt())) {
            throw new BizException(Code.ACTIVITY_NOT_ONGOING, "活动已结束");
        }
    }

    /** 库存必须充足（>= 申购数量）；返回所需扣减后的剩余量（便于调用方校验语义一致）。 */
    public static void assertStock(MarketingActivity a, int qty) {
        if (a.getStock() == null || a.getStock() < qty) {
            throw new BizException(Code.STOCK_NOT_ENOUGH, "活动库存不足");
        }
    }

    /** 非抛异常的库存可扣减判定（供纯函数单测 / 前端展示用）。 */
    public static boolean canDeduct(Integer stock, int qty) {
        return stock != null && stock >= qty;
    }

    /** 每人参与次数不得超过上限（limit<=0 表示不限，REQ-03）。 */
    public static void assertUserLimit(int joined, int limit) {
        if (limit > 0 && joined >= limit) {
            throw new BizException(Code.ACTIVITY_JOIN_LIMIT, "已超过活动参与上限");
        }
    }

    /**
     * 人群定向校验（REQ-13）：在可见性与参与校验同时生效。
     * 用户为空（未登录上下文）时除 ALL 外一律拒绝。
     */
    public static void assertAudience(MarketingActivity a, User user, LocalDateTime now) {
        AudienceType type = AudienceType.of(a.getAudienceType());
        switch (type) {
            case ALL:
                return;
            case NEW_USER:
                if (user == null || user.getCreatedAt() == null) {
                    throw new BizException(Code.AUDIENCE_NOT_MATCH, "请先登录");
                }
                int days = parseDays(a.getAudienceValue(), 30);
                if (user.getCreatedAt().plusDays(days).isBefore(now)) {
                    throw new BizException(Code.AUDIENCE_NOT_MATCH, "本活动仅限新用户参与");
                }
                return;
            case LEVEL:
                if (user == null || user.getCreditScore() == null) {
                    throw new BizException(Code.AUDIENCE_NOT_MATCH, "您暂不符合参与等级");
                }
                int minScore = parseInt(a.getAudienceValue(), 0);
                if (user.getCreditScore() < minScore) {
                    throw new BizException(Code.AUDIENCE_NOT_MATCH, "您的会员等级不足，无法参与");
                }
                return;
            case REALNAME:
                if (user == null || user.getRealNameVerified() == null || user.getRealNameVerified() != 1) {
                    throw new BizException(Code.AUDIENCE_NOT_MATCH, "本活动需完成实名认证");
                }
                return;
            default:
                return;
        }
    }

    /**
     * 亏损风险阻断（REQ-14）：砍价底价低于成本时，配置阶段即阻断或要求审批。
     * 纯函数：底价（分）必须 ≥ 成本（分），否则抛 ACTIVITY_LOSS_RISK。
     *
     * @param floorPriceFen 玩法底价 / 秒杀价（分）
     * @param costFen       成本线（分）；<=0 表示未设置成本，不拦截
     */
    public static void assertLossSafe(long floorPriceFen, long costFen) {
        if (costFen > 0 && floorPriceFen < costFen) {
            throw new BizException(Code.ACTIVITY_LOSS_RISK,
                    "活动底价 " + floorPriceFen + " 分低于成本 " + costFen + " 分，存在亏损风险");
        }
    }

    /** 活动是否处于预热/进行中（供缓存预热判定，不抛异常）。 */
    public static boolean isOpen(MarketingActivity a, LocalDateTime now) {
        if (a == null || !"ONGOING".equals(a.getStatus())) {
            return false;
        }
        if (a.getStartAt() != null && now.isBefore(a.getStartAt())) {
            return false;
        }
        return !(a.getEndAt() != null && now.isAfter(a.getEndAt()));
    }

    private static int parseDays(String v, int def) {
        return parseInt(v, def);
    }

    private static int parseInt(String v, int def) {
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
