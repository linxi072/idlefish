package com.idlefish.trade.notify.channel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信订阅消息请求体构造（纯函数，无副作用，离线可单测）。
 * 参考微信 subscribeMessage.send 规范：data 下每个关键词字段形如 {@code {"value": "..."}}，
 * 且 thing 类型长度上限 20 字符（超出会被微信拒绝），故做截断兜底。
 */
public final class WxSubscribeMessageBuilder {

    private WxSubscribeMessageBuilder() {
    }

    /** thing 类型字段值最大长度（微信限制）。 */
    public static final int THING_MAX = 20;

    /**
     * 构造订阅消息 data 段（默认模板关键词：thing1=标题、thing2=内容）。
     * 超出长度的字段值按 {@link #THING_MAX} 截断，避免被微信服务端拒绝。
     */
    public static Map<String, Object> buildData(String title, String content) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing1", field(trim(title, THING_MAX)));
        data.put("thing2", field(trim(content, THING_MAX)));
        return data;
    }

    /**
     * 构造完整订阅消息请求体。
     *
     * @param openid     接收者微信 openid
     * @param templateId 订阅消息模板 ID（由配置按 NotificationType.code 映射）
     * @param page       点击消息跳转小程序页面（可空）
     * @param title      标题（映射 thing1）
     * @param content    内容（映射 thing2）
     */
    public static Map<String, Object> buildBody(String openid, String templateId, String page,
                                                String title, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        if (page != null && !page.isBlank()) {
            body.put("page", page);
        }
        body.put("data", buildData(title, content));
        return body;
    }

    private static Map<String, Object> field(String value) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("value", value == null ? "" : value);
        return f;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
