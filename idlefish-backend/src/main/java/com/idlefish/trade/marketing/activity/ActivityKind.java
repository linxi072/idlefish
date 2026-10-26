package com.idlefish.trade.marketing.activity;

/**
 * 活动玩法类型（ITER-F08 统一活动内核，REQ-01）。
 * 三类玩法（砍价 / 抽奖 / 直播秒杀）共享同一内核，仅玩法特有规则不同。
 */
public enum ActivityKind {
    BARGAIN("BARGAIN", "砍价"),
    LOTTERY("LOTTERY", "抽奖"),
    LIVE_SECKILL("LIVE_SECKILL", "直播秒杀");

    private final String code;
    private final String label;

    ActivityKind(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** 由字符串解析；未知类型返回 null（由调用方抛 ACTIVITY_KIND_NOT_SUPPORTED）。 */
    public static ActivityKind of(String code) {
        if (code == null) {
            return null;
        }
        for (ActivityKind k : values()) {
            if (k.code.equals(code)) {
                return k;
            }
        }
        return null;
    }
}
