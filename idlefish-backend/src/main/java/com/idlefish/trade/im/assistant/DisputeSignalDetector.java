package com.idlefish.trade.im.assistant;

import java.util.ArrayList;
import java.util.List;

/**
 * 纠纷信号检测纯函数（ITER-F06 / REQ-06 纠纷信号预警）。
 * <p>文本类信号（辱骂/威胁/假货指控/反复退款/要求平台介入）由关键词表命中；
 * 时间类信号（卖家超 24h 未响应）由服务层结合会话时间计算后并入。
 * 风险分 = 各信号权重之和（封顶 100），达到阈值即预警并提示一键发起维权（F-17）。</p>
 */
public final class DisputeSignalDetector {

    private DisputeSignalDetector() {
    }

    /** 纠纷信号类型与风险分权重。 */
    public enum Signal {
        /** 辱骂攻击。 */
        ABUSE("辱骂攻击", 20),
        /** 威胁投诉 / 曝光 / 起诉。 */
        THREAT("威胁投诉曝光", 25),
        /** 反复退款诉求。 */
        REPEAT_REFUND("反复退款诉求", 15),
        /** 假货 / 欺诈 / 货不对板指控。 */
        FAKE_ACCUSE("假货欺诈指控", 30),
        /** 明确要求平台介入。 */
        PLATFORM_ESCALATE("要求平台介入", 25),
        /** 卖家超时未响应（服务层按时间计算）。 */
        SLOW_REPLY("卖家超时未响应", 20);

        private final String desc;
        private final int weight;

        Signal(String desc, int weight) {
            this.desc = desc;
            this.weight = weight;
        }

        public String getDesc() {
            return desc;
        }

        public int getWeight() {
            return weight;
        }
    }

    /** 风险等级。 */
    public enum Level {
        NONE, WARN, HIGH
    }

    private static final List<String> ABUSE_WORDS = List.of(
            "傻子", "傻逼", "废物", "垃圾卖家", "去死", "脑残", "智障", "贱人", "狗东西", "滚蛋", "神经病");

    private static final List<String> THREAT_WORDS = List.of(
            "投诉你", "举报你", "曝光你", "曝光到", "工商", "315", "消协", "报警", "法院", "起诉", "发帖", "小红书", "抖音");

    private static final List<String> REPEAT_REFUND_WORDS = List.of(
            "到底退不退", "再不退", "最后一次", "第几次", "一直不退款", "还不退款", "天天问", "催了");

    private static final List<String> FAKE_ACCUSE_WORDS = List.of(
            "假货", "是假的", "造假", "仿品", "山寨", "以次充好", "货不对板", "与描述不符", "欺诈", "骗我", "骗子", "套路");

    private static final List<String> PLATFORM_ESCALATE_WORDS = List.of(
            "平台介入", "客服介入", "人工客服", "平台处理", "小二", "客服接管", "申请仲裁", "平台客服");

    /** 文本信号检测（SLOW_REPLY 由服务层按时间补充）。 */
    public static List<Signal> detect(String text) {
        List<Signal> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String t = text.toLowerCase();
        if (anyHit(t, ABUSE_WORDS)) {
            out.add(Signal.ABUSE);
        }
        if (anyHit(t, THREAT_WORDS)) {
            out.add(Signal.THREAT);
        }
        if (anyHit(t, REPEAT_REFUND_WORDS)) {
            out.add(Signal.REPEAT_REFUND);
        }
        if (anyHit(t, FAKE_ACCUSE_WORDS)) {
            out.add(Signal.FAKE_ACCUSE);
        }
        if (anyHit(t, PLATFORM_ESCALATE_WORDS)) {
            out.add(Signal.PLATFORM_ESCALATE);
        }
        return out;
    }

    /** 风险分 = 权重和，封顶 100。 */
    public static int riskScore(List<Signal> signals) {
        if (signals == null || signals.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (Signal s : signals) {
            sum += s.getWeight();
        }
        return Math.min(100, sum);
    }

    /**
     * 风险等级：&ge;85 为 HIGH；&ge; warnThreshold 为 WARN；其余 NONE。
     *
     * @param warnThreshold 预警阈值（运营配置，默认 60）
     */
    public static Level level(int score, int warnThreshold) {
        if (score >= 85) {
            return Level.HIGH;
        }
        if (score >= warnThreshold) {
            return Level.WARN;
        }
        return Level.NONE;
    }

    private static boolean anyHit(String text, List<String> words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
