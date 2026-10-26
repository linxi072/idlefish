package com.idlefish.trade.im.assistant;

import java.util.List;

/**
 * 意图识别纯函数（ITER-F06 / REQ-03）。
 * <p>关键词命中计分，裁决类（{@link ImIntent#RULING}）优先判定：只要命中即返回 RULING，
 * 由上层直接转人工（REQ-04 禁自动作答）；其次纠纷类；其余按命中数与固定优先级取最高分。
 * 纯函数、无副作用、可离线单测。</p>
 */
public final class IntentDetector {

    private IntentDetector() {
    }

    // ===== 裁决类（REQ-04）：涉及责任判定 / 赔付金额 / 平台强制介入，禁自动作答 =====
    private static final List<String> RULING_WORDS = List.of(
            "谁的责任", "责任在谁", "算谁的", "该不该退", "能不能退", "可不可以退", "应不应该退",
            "赔多少", "怎么赔", "如何赔", "赔付金额", "判责", "仲裁", "平台介入", "客服介入", "人工介入",
            "假货怎么办", "假的怎么办", "算欺诈", "属于欺诈", "欺诈吗", "违法吗", "假一赔三",
            "投诉到", "工商", "消协", "法院", "起诉");

    // ===== 纠纷吐槽（REQ-06 纠纷信号）：命中进纠纷预警链路 =====
    private static final List<String> DISPUTE_WORDS = List.of(
            "骗子", "骗我", "坑人", "太坑", "套路", "欺诈", "举报", "曝光你", "曝光到", "发帖",
            "货不对板", "与描述不符", "以次充好", "假货", "是假的", "山寨", "仿品", "差评", "垃圾卖家");

    // ===== 发货咨询 =====
    private static final List<String> SHIPPING_WORDS = List.of(
            "发货", "什么时候发", "多久发", "几天发", "啥时发", "发出", "顺丰", "包邮", "包邮吗", "运费", "快递费", "现货", "有货");

    // ===== 议价咨询（注意：不代接受报价，仅可回复平台规则，见边界场景） =====
    private static final List<String> PRICE_WORDS = List.of(
            "便宜", "优惠", "打折", "降价", "少一点", "低一点", "议价", "砍价", "多少钱", "什么价", "价格", "还能少");

    // ===== 退换售后 =====
    private static final List<String> REFUND_WORDS = List.of(
            "退款", "退货", "换货", "售后", "退换", "七天无理由", "无理由", "退掉", "退了", "退回");

    // ===== 鉴定验货 =====
    private static final List<String> INSPECTION_WORDS = List.of(
            "验货", "鉴定", "质检", "验机", "正品", "真的假的", "是假的吗", "真伪", "成色", "序列号", "官网验证", "官方验", "走平台验");

    // ===== 物流查询 =====
    private static final List<String> LOGISTICS_WORDS = List.of(
            "物流", "快递到哪", "到哪了", "到哪儿了", "单号", "签收", "运输", "发货了吗", "多久到", "几天到", "收不到", "怎么还没到");

    // ===== 闲聊寒暄 =====
    private static final List<String> CHITCHAT_WORDS = List.of(
            "你好", "您好", "在吗", "在么", "hello", "hi", "嗨", "谢谢", "感谢", "早安", "晚安", "早上好", "下午好");

    /** 平分时的固定优先级（下标越小越优先）。 */
    private static final List<ImIntent> TIE_ORDER = List.of(
            ImIntent.REFUND, ImIntent.PRICE, ImIntent.INSPECTION, ImIntent.SHIPPING,
            ImIntent.LOGISTICS, ImIntent.CHITCHAT);

    /**
     * 识别单条文本意图。
     *
     * @param text 买家消息原文（可为空）
     * @return 意图；无任何命中返回 {@link ImIntent#UNKNOWN}
     */
    public static ImIntent detect(String text) {
        if (text == null || text.isBlank()) {
            return ImIntent.UNKNOWN;
        }
        String t = text.toLowerCase();
        if (hits(t, RULING_WORDS) > 0) {
            return ImIntent.RULING; // REQ-04：裁决类直转人工，优先级最高
        }
        if (hits(t, DISPUTE_WORDS) > 0) {
            return ImIntent.DISPUTE;
        }
        int best = 0;
        ImIntent bestIntent = ImIntent.UNKNOWN;
        for (ImIntent intent : TIE_ORDER) {
            int score = hits(t, wordsOf(intent));
            if (score > best) {
                best = score;
                bestIntent = intent;
            }
        }
        return bestIntent;
    }

    /** 指定意图的关键词表（供测试与知识库运营核对）。 */
    static List<String> wordsOf(ImIntent intent) {
        switch (intent) {
            case RULING:
                return RULING_WORDS;
            case DISPUTE:
                return DISPUTE_WORDS;
            case SHIPPING:
                return SHIPPING_WORDS;
            case PRICE:
                return PRICE_WORDS;
            case REFUND:
                return REFUND_WORDS;
            case INSPECTION:
                return INSPECTION_WORDS;
            case LOGISTICS:
                return LOGISTICS_WORDS;
            case CHITCHAT:
                return CHITCHAT_WORDS;
            default:
                return List.of();
        }
    }

    private static int hits(String text, List<String> words) {
        int n = 0;
        for (String w : words) {
            if (text.contains(w)) {
                n++;
            }
        }
        return n;
    }
}
