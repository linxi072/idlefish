package com.idlefish.trade.im.assistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纠纷信号检测纯函数单测（ITER-F06 / REQ-06）。
 */
class DisputeSignalDetectorTest {

    @Test
    void detectsAbuse() {
        List<DisputeSignalDetector.Signal> signals = DisputeSignalDetector.detect("你这个骗子，傻逼");
        assertTrue(signals.contains(DisputeSignalDetector.Signal.ABUSE));
        assertTrue(signals.contains(DisputeSignalDetector.Signal.FAKE_ACCUSE));
    }

    @Test
    void detectsThreatAndPlatformEscalate() {
        assertTrue(DisputeSignalDetector.detect("不退款我就投诉你，315见")
                .contains(DisputeSignalDetector.Signal.THREAT));
        assertTrue(DisputeSignalDetector.detect("我要平台介入")
                .contains(DisputeSignalDetector.Signal.PLATFORM_ESCALATE));
    }

    @Test
    void detectsRepeatRefund() {
        assertTrue(DisputeSignalDetector.detect("到底退不退，最后一次问你")
                .contains(DisputeSignalDetector.Signal.REPEAT_REFUND));
    }

    @Test
    void noSignalForNormalText() {
        assertTrue(DisputeSignalDetector.detect("什么时候发货").isEmpty());
        assertTrue(DisputeSignalDetector.detect(null).isEmpty());
    }

    @Test
    void riskScoreCappedAt100() {
        int score = DisputeSignalDetector.riskScore(List.of(
                DisputeSignalDetector.Signal.ABUSE,
                DisputeSignalDetector.Signal.THREAT,
                DisputeSignalDetector.Signal.FAKE_ACCUSE,
                DisputeSignalDetector.Signal.PLATFORM_ESCALATE,
                DisputeSignalDetector.Signal.REPEAT_REFUND));
        assertEquals(100, score); // 20+25+30+25+15=115 → 封顶 100
    }

    @Test
    void levelThresholds() {
        assertEquals(DisputeSignalDetector.Level.NONE, DisputeSignalDetector.level(59, 60));
        assertEquals(DisputeSignalDetector.Level.WARN, DisputeSignalDetector.level(60, 60));
        assertEquals(DisputeSignalDetector.Level.HIGH, DisputeSignalDetector.level(85, 60));
    }
}
