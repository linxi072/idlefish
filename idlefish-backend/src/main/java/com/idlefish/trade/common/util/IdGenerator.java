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

    public static String fundNo() {
        return next("FD");
    }

    /** 维权工单号（F-17）。 */
    public static String disputeNo() {
        return next("DP");
    }

    /** 验货单号（F-02）。 */
    public static String inspectionNo() {
        return next("IV");
    }

    /** 鉴定报告编号（F-02）。 */
    public static String reportNo() {
        return next("RP");
    }

    /** 回收单号（ITER-F05 同城回收）。 */
    public static String recycleNo() {
        return next("RC");
    }

    /** 同城配送单号（ITER-F05 本地履约）。 */
    public static String deliveryNo() {
        return next("LD");
    }

    /** 数据范围临时授权单号（ITER-T05 多租户/机构数据隔离深化）。 */
    public static String grantNo() {
        return next("GR");
    }

    /** 导出任务单号（ITER-T06 运营数据导出中心）。 */
    public static String exportNo() {
        return next("EXP");
    }

    /** 事件回放任务单号（ITER-T06 事件驱动架构；EVR 前缀避免与鉴定报告 RP 混淆）。 */
    public static String replayNo() {
        return next("EVR");
    }

    /** 权益兑换单号（ITER-F07 会员积分成长深化；BX 前缀避免与回收 RC 混淆）。 */
    public static String benefitExchangeNo() {
        return next("BX");
    }

    /** 智能助手应答单号（ITER-F06 IM 客服智能化；AR 前缀）。 */
    public static String assistantReplyNo() {
        return next("AR");
    }

    /** 纠纷预警单号（ITER-F06；DW 前缀）。 */
    public static String disputeWarningNo() {
        return next("DW");
    }

    /** 营销活动单号（ITER-F08 营销玩法扩展；MA 前缀避免与券 C/会员 M 混淆）。 */
    public static String activityNo() {
        return next("MA");
    }

    /** 活动参与记录单号（ITER-F08；MR 前缀）。 */
    public static String activityRecordNo() {
        return next("MR");
    }

    /** 砍价会话单号（ITER-F08；BS 前缀）。 */
    public static String bargainSessionNo() {
        return next("BS");
    }

    /** 客服工单号（F-12）。 */
    public static String workOrderNo() {
        return next("WO");
    }
}
