package com.idlefish.trade.common.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务单号生成（订单号 / 支付号 / 退款号 / 结算号）。
 * 基于纳秒时间 + 自增序列，单体环境保证唯一；分布式需换为雪花或号段。
 */
public class IdGenerator {

    private static final AtomicLong SEQ = new AtomicLong(0);

    private static String next(String prefix) {
        long t = System.nanoTime();
        long s = SEQ.incrementAndGet();
        return prefix + Long.toString(t, 36).toUpperCase() + s;
    }

    public static String orderNo() {
        return next("NO");
    }

    public static String payNo() {
        return next("PAY");
    }

    public static String refundNo() {
        return next("RF");
    }

    public static String settleNo() {
        return next("ST");
    }
}
