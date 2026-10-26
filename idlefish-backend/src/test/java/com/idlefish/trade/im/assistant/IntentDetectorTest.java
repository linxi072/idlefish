package com.idlefish.trade.im.assistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 意图识别纯函数单测（ITER-F06 / REQ-03/04）。
 */
class IntentDetectorTest {

    @Test
    void detectShipping() {
        assertEquals(ImIntent.SHIPPING, IntentDetector.detect("请问什么时候发货？"));
        assertEquals(ImIntent.SHIPPING, IntentDetector.detect("多久能到，包邮吗"));
    }

    @Test
    void detectPrice() {
        assertEquals(ImIntent.PRICE, IntentDetector.detect("能便宜点吗"));
        assertEquals(ImIntent.PRICE, IntentDetector.detect("这个多少钱"));
    }

    @Test
    void detectRefund() {
        assertEquals(ImIntent.REFUND, IntentDetector.detect("怎么申请退款"));
        assertEquals(ImIntent.REFUND, IntentDetector.detect("支持七天无理由吗"));
    }

    @Test
    void detectInspection() {
        assertEquals(ImIntent.INSPECTION, IntentDetector.detect("是正品吗，支持验货吗"));
        assertEquals(ImIntent.INSPECTION, IntentDetector.detect("成色怎么样"));
    }

    @Test
    void detectLogistics() {
        assertEquals(ImIntent.LOGISTICS, IntentDetector.detect("快递到哪了"));
        assertEquals(ImIntent.LOGISTICS, IntentDetector.detect("发货了吗，多久到"));
    }

    @Test
    void detectChitchat() {
        assertEquals(ImIntent.CHITCHAT, IntentDetector.detect("你好"));
        assertEquals(ImIntent.CHITCHAT, IntentDetector.detect("在吗"));
    }

    @Test
    void detectDispute() {
        assertEquals(ImIntent.DISPUTE, IntentDetector.detect("你是骗子吧"));
        assertEquals(ImIntent.DISPUTE, IntentDetector.detect("货不对板，我要投诉"));
    }

    /** REQ-04：裁决类优先于一切其它意图，命中即转人工。 */
    @Test
    void detectRulingWinsOverAll() {
        assertEquals(ImIntent.RULING, IntentDetector.detect("这到底谁的责任，能不能退，赔多少"));
        assertEquals(ImIntent.RULING, IntentDetector.detect("平台介入处理"));
        assertEquals(ImIntent.RULING, IntentDetector.detect("算欺诈吗"));
    }

    @Test
    void detectUnknownAndBlank() {
        assertEquals(ImIntent.UNKNOWN, IntentDetector.detect("哈哈哈哈哈哈哈哈"));
        assertEquals(ImIntent.UNKNOWN, IntentDetector.detect(""));
        assertEquals(ImIntent.UNKNOWN, IntentDetector.detect(null));
    }

    @Test
    void autoAnswerableExcludesRuling() {
        assertEquals(false, ImIntent.RULING.autoAnswerable());
        assertEquals(true, ImIntent.SHIPPING.autoAnswerable());
        assertEquals(true, ImIntent.UNKNOWN.autoAnswerable());
    }
}
