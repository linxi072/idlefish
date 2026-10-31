package com.idlefish.trade.compliance;

import java.time.LocalTime;

/**
 * 未成年人保护守卫（ITER-U05 REQ-04/REQ-05/REQ-06）：纯函数，无外部依赖，离线可单测。
 *
 * <p>口径：
 * <ul>
 *   <li>成年：{@code age >= 18}；未成年：{@code age < 18}（其中 {@code age < 14} 为儿童）。</li>
 *   <li>受限操作：交易下单、支付、提现、线下见面引导 —— 未成年人一律禁止（REQ-04）。</li>
 *   <li>时段限制：22:00–06:00 为宵禁时段，未成年人不可用（REQ-05）。</li>
 *   <li>时长限制：每日累计可用时长默认 40 分钟，超出即限（REQ-05）。</li>
 *   <li>拒绝必须给出明确原因与申诉/监护人路径，<b>不得静默拒绝</b>（REQ-06）。</li>
 * </ul>
 *
 * <p>年龄未知（未实名）时 {@link #ageBand(Integer)} 返回 {@code UNKNOWN}，按「非未成年」放行，
 * 避免把数据缺失误判为未成年而阻断正常交易；一旦实名确认即严格生效。
 */
public final class MinorGuard {

    /** 成年年龄下限。 */
    public static final int ADULT_AGE = 18;
    /** 儿童年龄上限（未保条例对低龄段有更严格要求）。 */
    public static final int CHILD_AGE = 14;
    /** 未成年人每日默认可用时长（分钟）。 */
    public static final int DEFAULT_DAILY_MINUTES = 40;

    private static final LocalTime QUIET_FROM = LocalTime.of(22, 0);
    private static final LocalTime QUIET_TO = LocalTime.of(6, 0);

    private MinorGuard() {
    }

    /** 受限操作枚举：未成年人模式下禁止的行为。 */
    public enum RestrictedAction {
        TRADE("下单交易"),
        PAY("支付"),
        WITHDRAW("提现"),
        OFFLINE_MEET("线下见面/同城自提");

        private final String desc;

        RestrictedAction(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    /** 是否未成年（年龄未知视为非未成年）。 */
    public static boolean isMinor(Integer age) {
        return age != null && age < ADULT_AGE;
    }

    /** 年龄段：CHILD(<14) / TEEN(14–17) / ADULT(>=18) / UNKNOWN(未实名)。 */
    public static String ageBand(Integer age) {
        if (age == null) {
            return "UNKNOWN";
        }
        if (age < CHILD_AGE) {
            return "CHILD";
        }
        if (age < ADULT_AGE) {
            return "TEEN";
        }
        return "ADULT";
    }

    /** 该操作在未成年人模式下是否受限。 */
    public static boolean restricted(RestrictedAction action) {
        return action != null;
    }

    /** 宵禁时段：22:00（含）至次日 06:00（不含）。 */
    public static boolean withinQuietHours(LocalTime time) {
        if (time == null) {
            return false;
        }
        return !time.isBefore(QUIET_FROM) || time.isBefore(QUIET_TO);
    }

    /** 当日累计时长是否超限。 */
    public static boolean exceedsDailyLimit(int usedMinutes, int limitMinutes) {
        int limit = limitMinutes <= 0 ? DEFAULT_DAILY_MINUTES : limitMinutes;
        return usedMinutes >= limit;
    }

    /**
     * 综合放行判定（REQ-04/REQ-05）。
     *
     * @param minor        是否未成年
     * @param action       待执行操作（null 视为不限）
     * @param time         当前时间（用于宵禁判定）
     * @param usedMinutes  当日已用时长
     * @param limitMinutes 当日时长上限（<=0 时取 {@link #DEFAULT_DAILY_MINUTES}）
     * @return true 表示放行
     */
    public static boolean allowed(boolean minor, RestrictedAction action, LocalTime time,
                                  int usedMinutes, int limitMinutes) {
        return denyReason(minor, action, time, usedMinutes, limitMinutes) == null;
    }

    /**
     * 拒绝原因（REQ-06）：放行时返回 null，拒绝时返回**可直接展示给用户**的明确原因，
     * 含监护人确认/申诉路径提示，禁止静默拒绝。
     */
    public static String denyReason(boolean minor, RestrictedAction action, LocalTime time,
                                    int usedMinutes, int limitMinutes) {
        if (!minor) {
            return null;
        }
        if (withinQuietHours(time)) {
            return "根据未成年人网络保护要求，22:00–06:00 为保护时段，暂不可用；如需继续使用，请在监护人同意后于非保护时段操作。";
        }
        if (exceedsDailyLimit(usedMinutes, limitMinutes)) {
            int limit = limitMinutes <= 0 ? DEFAULT_DAILY_MINUTES : limitMinutes;
            return "今日可用时长已达上限（" + limit + " 分钟）；如需延长，请由监护人在「我的-合规中心」确认后申请。";
        }
        if (restricted(action)) {
            return "未成年人账号不支持" + action.getDesc() + "；如需继续，请由监护人在「我的-合规中心」确认，或完成实名认证后再操作。";
        }
        return null;
    }
}
