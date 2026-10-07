package com.idlefish.trade.inspection;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 鉴定验货费用与建议纯函数（F-02）：无状态、可离线单测。
 *
 * <p>REQ-10 服务费按「分」结算并入资金流水；REQ-08 高价值品类建议验货（非强制）。
 */
public final class InspectionFeeCalculator {

    /** 服务费率（万分之一单位）：2% = 200 bps。 */
    public static final int FEE_RATE_BPS = 200;
    /** 最低服务费（分）：5 元。 */
    public static final long MIN_FEE_FEN = 500L;
    /** 最高服务费（分）：200 元。 */
    public static final long MAX_FEE_FEN = 20000L;
    /** 建议验货价格阈值（分）：2000 元，超过即建议验货。 */
    public static final long SUGGEST_PRICE_THRESHOLD_FEN = 200_000L;

    /** 高价值/高风险品类（建议验货，非强制）。 */
    public static final Set<String> HIGH_VALUE_CATEGORIES = Collections.unmodifiableSet(
            new HashSet<>(java.util.Arrays.asList("3C", "LUXURY", "WATCH", "CAMERA", "INSTRUMENT")));

    private InspectionFeeCalculator() {
    }

    /**
     * 计算验货服务费（分）。
     * <p>公式：price * rate / 10000，下限 {@link #MIN_FEE_FEN}，上限 {@link #MAX_FEE_FEN}。
     * 价格非正时返回 0（不可收费）。全程整型运算，避免浮点资损。
     *
     * @param priceInFen 商品成交价（分）
     * @return 服务费（分）
     */
    public static long calcServiceFee(long priceInFen) {
        if (priceInFen <= 0) {
            return 0L;
        }
        long fee = priceInFen * FEE_RATE_BPS / 10_000L;
        if (fee < MIN_FEE_FEN) {
            fee = MIN_FEE_FEN;
        }
        if (fee > MAX_FEE_FEN) {
            fee = MAX_FEE_FEN;
        }
        return fee;
    }

    /**
     * REQ-08 是否建议验货（非强制）。
     * 命中价格阈值或属高价值品类即建议，但仅为建议，不阻断下单。
     *
     * @param priceInFen   商品成交价（分）
     * @param categoryCode 品类编码（如 3C / LUXURY / 普通品类）
     * @return 是否建议验货
     */
    public static boolean suggestInspection(long priceInFen, String categoryCode) {
        if (priceInFen >= SUGGEST_PRICE_THRESHOLD_FEN) {
            return true;
        }
        return categoryCode != null && HIGH_VALUE_CATEGORIES.contains(categoryCode);
    }
}
