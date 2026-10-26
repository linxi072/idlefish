package com.idlefish.trade.im.assistant;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏纯函数（ITER-F06 / REQ-13 不回显手机号/地址/验证码）。
 * <p>在三个位置生效：LLM 生成内容发出前、未命中问题池落库前、拦截记录留痕前。
 * 手机号保留前 3 后 4；QQ/微信号/验证码/详细地址整体打码。纯函数、无副作用。</p>
 */
public final class SensitiveInfoMasker {

    private SensitiveInfoMasker() {
    }

    /** 打码掩码。 */
    public static final String MASK = "****";

    /** 手机号（11 位，避免命中更长数字串中的片段；组1=前3位，中间4位，组2=后4位）。 */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");

    /** QQ 号（5 位以上，兼容「QQ：123456」「qq123456」）。 */
    private static final Pattern QQ = Pattern.compile("(?i)(qq\\s*[:：]?\\s*)([1-9]\\d{4,})");

    /** 微信号（字母数字下划线 5 位以上，兼容「微信号是abc123」）。 */
    private static final Pattern WECHAT = Pattern.compile("(?i)(微信(?:号)?\\s*(?:是|为|叫)?\\s*[:：]?\\s*)([A-Za-z0-9_-]{5,30})");

    /** 验证码 / 校验码 / 动态码及其后 4~8 位数字。 */
    private static final Pattern CAPTCHA = Pattern.compile("((?:验证码|校验码|动态码)[^\\d]{0,8})(\\d{4,8})");

    /** 关键词引导的详细地址（「地址：xxx」「寄到xxx」）。 */
    private static final Pattern ADDRESS_KEYWORD = Pattern.compile(
            "((?:收货地址|详细地址|地址|住在|送到|寄到|收货信息)\\s*[:：]?\\s*)(\\S{4,})");

    /** 行政区划 + 门牌细节的地址片段（「上海市杨浦区xx路12号」）。 */
    private static final Pattern ADDRESS_DETAIL = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,6}(?:省|市|自治区))[\\u4e00-\\u9fa5A-Za-z0-9]{2,24}(?:路|街|巷|号|小区|栋|单元|室|层|公寓|大厦)");

    /**
     * 脱敏：命中手机号/QQ/微信号/验证码/地址的模式一律打码后返回；无命中原样返回。
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = PHONE.matcher(text).replaceAll("$1" + MASK + "$2");
        out = QQ.matcher(out).replaceAll("$1" + MASK);
        out = WECHAT.matcher(out).replaceAll("$1" + MASK);
        out = CAPTCHA.matcher(out).replaceAll("$1" + MASK);
        out = ADDRESS_KEYWORD.matcher(out).replaceAll("$1" + MASK);
        out = ADDRESS_DETAIL.matcher(out).replaceAll("$1" + MASK);
        return out;
    }

    /** 是否命中任一敏感模式（供拦截记录与 LLM 输出安全校验）。 */
    public static boolean hasSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return PHONE.matcher(text).find() || QQ.matcher(text).find() || WECHAT.matcher(text).find()
                || CAPTCHA.matcher(text).find() || ADDRESS_KEYWORD.matcher(text).find()
                || ADDRESS_DETAIL.matcher(text).find();
    }

    /** 仅手机号命中（供测试与风控细分）。 */
    public static boolean hasPhone(String text) {
        return text != null && PHONE.matcher(text).find();
    }

    /** 仅地址命中（供测试与风控细分）。 */
    public static boolean hasAddress(String text) {
        return text != null && (ADDRESS_KEYWORD.matcher(text).find() || ADDRESS_DETAIL.matcher(text).find());
    }

    /** 正则自检入口（包级，供单测验证模式串本身可编译）。 */
    static Matcher phoneMatcher(String text) {
        return PHONE.matcher(text);
    }
}
