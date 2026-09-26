package com.idlefish.trade.common.util;

/**
 * 金额工具：统一「分 → 元」换算，消除各 service 中重复的同构私有方法。
 */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    /** 分转元（Double，保留精度用于展示）。null 安全：入参为 null 时返回 null。 */
    public static Double fenToYuan(Long fen) {
        return fen == null ? null : fen / 100.0;
    }
}
