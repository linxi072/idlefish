package com.idlefish.trade.compliance;

/**
 * 个人信息脱敏（ITER-U05 REQ-11）：纯函数，无外部依赖，离线可单测。
 *
 * <p>口径：手机号/身份证/银行卡/收货地址/昵称一律按需最小化展示；非必要场景（列表、导出、
 * 日志、客服视图）只输出脱敏值。脱敏为**不可逆展示层处理**，不改动落库原文。
 */
public final class PersonalInfoMasker {

    private static final String STARS = "****";
    private static final String EMPTY = "";

    private PersonalInfoMasker() {
    }

    /** 手机号脱敏：138****8000；长度不足时整体掩码。 */
    public static String maskPhone(String phone) {
        if (isBlank(phone)) {
            return EMPTY;
        }
        String v = phone.trim();
        int len = v.length();
        if (len >= 11) {
            return v.substring(0, 3) + STARS + v.substring(len - 4);
        }
        if (len >= 7) {
            return v.substring(0, 2) + STARS + v.substring(len - 2);
        }
        return STARS;
    }

    /** 身份证脱敏：前 6 后 4（18 位）；其余按通用规则。 */
    public static String maskIdCard(String idCard) {
        if (isBlank(idCard)) {
            return EMPTY;
        }
        String v = idCard.trim();
        int len = v.length();
        if (len >= 18) {
            return v.substring(0, 6) + STARS + STARS + v.substring(len - 4);
        }
        if (len >= 8) {
            return v.substring(0, 2) + STARS + v.substring(len - 2);
        }
        return STARS;
    }

    /** 银行卡脱敏：仅保留后 4 位。 */
    public static String maskBankCard(String cardNo) {
        if (isBlank(cardNo)) {
            return EMPTY;
        }
        String v = cardNo.trim().replaceAll("\\s+", EMPTY);
        int len = v.length();
        if (len <= 4) {
            return STARS;
        }
        return STARS + " " + v.substring(len - 4);
    }

    /** 收货地址脱敏：保留前 6 个字符（省市/区），隐藏门牌详情。 */
    public static String maskAddress(String address) {
        if (isBlank(address)) {
            return EMPTY;
        }
        String v = address.trim();
        if (v.length() <= 6) {
            return STARS;
        }
        return v.substring(0, 6) + STARS + "(详细地址已隐藏)";
    }

    /** 昵称脱敏：保留首字符。 */
    public static String maskNickname(String nickname) {
        if (isBlank(nickname)) {
            return EMPTY;
        }
        String v = nickname.trim();
        if (v.length() <= 1) {
            return "*";
        }
        return v.substring(0, 1) + STARS;
    }

    /** 邮箱脱敏：保留首字符与域名。 */
    public static String maskEmail(String email) {
        if (isBlank(email)) {
            return EMPTY;
        }
        String v = email.trim();
        int at = v.indexOf('@');
        if (at <= 0) {
            return STARS;
        }
        return v.substring(0, 1) + STARS + v.substring(at);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
