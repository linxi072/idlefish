package com.idlefish.trade.common.enums;

import lombok.Getter;

/**
 * 信用等级（F-06 信用体系）：基于信用分映射。
 * 优秀 ≥90 / 良好 75-89 / 一般 60-74 / 较差 <60。
 */
@Getter
public enum CreditLevel {

    EXCELLENT(90, 100, "优秀"),
    GOOD(75, 89, "良好"),
    FAIR(60, 74, "一般"),
    POOR(0, 59, "较差");

    private final int min;
    private final int max;
    private final String desc;

    CreditLevel(int min, int max, String desc) {
        this.min = min;
        this.max = max;
        this.desc = desc;
    }

    /** 由信用分推导等级（边界：90/75/60）。 */
    public static CreditLevel fromScore(int score) {
        if (score >= EXCELLENT.min) {
            return EXCELLENT;
        }
        if (score >= GOOD.min) {
            return GOOD;
        }
        if (score >= FAIR.min) {
            return FAIR;
        }
        return POOR;
    }
}
