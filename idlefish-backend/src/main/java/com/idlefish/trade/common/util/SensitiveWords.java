package com.idlefish.trade.common.util;

import java.util.regex.Pattern;

/**
 * 敏感词 / 站外联系方式折叠（PRD §E4、§B3 机审复用）。
 * 命中后统一替换为折叠标记，用于 IM 消息与商品发布的安全处置。
 * 生产可替换为阿里云/腾讯云内容安全，此为实现本地可运行的最小合规版本。
 */
public final class SensitiveWords {

    /** 涉诈 / 违规词。 */
    public static final String[] FRAUD_WORDS = {"代开发票", "色情", "赌博", "枪支", "刷单", "虚假交易", "套现"};

    /** 站外联系方式正则：手机号 / 微信 / QQ / 加我。 */
    private static final Pattern CONTACT = Pattern.compile(
            "(1[3-9]\\d{9})|(微信[：: ]?\\S+)|(微信号[：: ]?\\S+)|(QQ[：: ]?\\d+)|(qq[：: ]?\\d+)|(加我\\S{1,12})",
            Pattern.CASE_INSENSITIVE);

    /** 折叠标记。 */
    public static final String FOLDED = "***";

    private SensitiveWords() {
    }

    /** 折叠文本：返回 [折叠后文本, 是否命中]。 */
    public static String[] foldWithHit(String text) {
        if (text == null) {
            return new String[]{null, "false"};
        }
        boolean hit = false;
        StringBuilder sb = new StringBuilder(text);
        for (String w : FRAUD_WORDS) {
            int idx;
            while ((idx = sb.indexOf(w)) >= 0) {
                sb.replace(idx, idx + w.length(), FOLDED);
                hit = true;
            }
        }
        java.util.regex.Matcher m = CONTACT.matcher(sb);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, FOLDED);
            hit = true;
        }
        m.appendTail(out);
        return new String[]{out.toString(), String.valueOf(hit)};
    }

    /** 仅返回折叠后的文本。 */
    public static String fold(String text) {
        return foldWithHit(text)[0];
    }

    /** 是否命中敏感词 / 站外联系方式。 */
    public static boolean hit(String text) {
        return Boolean.parseBoolean(foldWithHit(text)[1]);
    }
}
