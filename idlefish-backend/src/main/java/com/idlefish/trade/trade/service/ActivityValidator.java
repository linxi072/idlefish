package com.idlefish.trade.trade.service;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.trade.entity.Activity;

import java.time.LocalDateTime;

/**
 * 活动业务校验纯函数（F-13.3）：无副作用，离线可单测。
 * 覆盖活动有效性、库存充足性、每人参与上限三类规则。
 */
public final class ActivityValidator {

    private ActivityValidator() {
    }

    /** 活动必须处于进行中且在时间窗内。 */
    public static void assertOngoing(Activity a, LocalDateTime now) {
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

    /** 活动库存必须充足（>= 申购数量）。 */
    public static void assertStock(Activity a, int qty) {
        if (a.getStock() == null || a.getStock() < qty) {
            throw new BizException(Code.STOCK_NOT_ENOUGH, "活动库存不足");
        }
    }

    /** 每人参与次数不得超过上限（limit<=0 表示不限）。 */
    public static void assertUserLimit(int joined, int limit) {
        if (limit > 0 && joined >= limit) {
            throw new BizException(Code.ACTIVITY_JOIN_LIMIT, "已超过活动参与上限");
        }
    }
}
