package com.idlefish.trade.compliance;

import com.idlefish.trade.notify.enums.NotificationType;

import java.util.EnumSet;
import java.util.Set;

/**
 * 通知分类（ITER-U05 REQ-09/REQ-10）：区分「营销类」与「交易必需类」。
 *
 * <p>监管要求：用户关闭营销类通知后，平台**不得**再下发营销消息；交易必需通知（支付/退款/物流/
 * 维权/系统告警等）必须保留，否则会造成履约与资金链路失联。
 *
 * <p>纯函数：无状态、无外部依赖，离线可单测。新增 {@link NotificationType} 时默认归入
 * {@link #ESSENTIAL}（保守口径）——只有显式登记到 {@link #MARKETING_TYPES} 的才会被开关拦截。
 */
public final class NotificationCategory {

    /** 营销类：可被用户一键关闭（REQ-09）。 */
    public static final String MARKETING = "marketing";
    /** 交易必需类：永不因营销开关被拦截。 */
    public static final String ESSENTIAL = "essential";

    private static final Set<NotificationType> MARKETING_TYPES = EnumSet.of(
            NotificationType.COUPON_CLAIMED,
            NotificationType.POINT_EARNED,
            NotificationType.GROWTH_EARNED,
            NotificationType.GROWTH_LEVEL_UPGRADED,
            NotificationType.GROWTH_DECAY_WARNING
    );

    private NotificationCategory() {
    }

    /** 判定通知类型是否为营销类。 */
    public static boolean isMarketing(NotificationType type) {
        return type != null && MARKETING_TYPES.contains(type);
    }

    /** 通知归类：{@link #MARKETING} 或 {@link #ESSENTIAL}。 */
    public static String of(NotificationType type) {
        return isMarketing(type) ? MARKETING : ESSENTIAL;
    }
}
