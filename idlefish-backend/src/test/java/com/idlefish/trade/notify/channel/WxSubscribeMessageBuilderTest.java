package com.idlefish.trade.notify.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 微信订阅消息请求体构造纯函数单测（F-14.4，离线可跑）。
 */
class WxSubscribeMessageBuilderTest {

    @Test
    @DisplayName("buildData：thing1=标题、thing2=内容，内容超 20 字截断至上限")
    void buildData() {
        // 内容 = 订单号(3)+1234567890(10)+已支付成功(5)+请尽快查收(5) = 23 字，应被截断为 20
        Map<String, Object> data = WxSubscribeMessageBuilder.buildData("您的订单已支付", "订单号1234567890已支付成功请尽快查收");
        Map<String, Object> f1 = (Map<String, Object>) data.get("thing1");
        Map<String, Object> f2 = (Map<String, Object>) data.get("thing2");
        assertEquals("您的订单已支付", f1.get("value"));
        assertEquals(WxSubscribeMessageBuilder.THING_MAX, ((String) f2.get("value")).length());
        assertEquals("订单号1234567890已支付成功请尽", f2.get("value"));
    }

    @Test
    @DisplayName("buildData：内容未超 20 字时保持原样")
    void buildDataNoTruncate() {
        Map<String, Object> data = WxSubscribeMessageBuilder.buildData("您的订单已支付", "订单号1234567890已支付成功");
        Map<String, Object> f2 = (Map<String, Object>) data.get("thing2");
        assertEquals("订单号1234567890已支付成功", f2.get("value"));
    }

    @Test
    @DisplayName("buildData：空值与超长标题安全截断")
    void buildDataTruncateTitle() {
        String longTitle = "一二三四五六七八九十十一十二十三十四十五十六十七十八十九二十二十一";
        Map<String, Object> data = WxSubscribeMessageBuilder.buildData(longTitle, null);
        Map<String, Object> f1 = (Map<String, Object>) data.get("thing1");
        assertEquals(WxSubscribeMessageBuilder.THING_MAX, ((String) f1.get("value")).length());
        Map<String, Object> f2 = (Map<String, Object>) data.get("thing2");
        assertEquals("", f2.get("value"));
    }

    @Test
    @DisplayName("buildBody：包含 touser/template_id/data，未传 page 则不含 page")
    void buildBodyWithoutPage() {
        Map<String, Object> body = WxSubscribeMessageBuilder.buildBody("OPENID", "TPL1", null, "标题", "内容");
        assertEquals("OPENID", body.get("touser"));
        assertEquals("TPL1", body.get("template_id"));
        assertTrue(body.containsKey("data"));
        assertTrue(!body.containsKey("page"));
    }

    @Test
    @DisplayName("buildBody：传入 page 时写入跳转页")
    void buildBodyWithPage() {
        Map<String, Object> body = WxSubscribeMessageBuilder.buildBody("OPENID", "TPL1", "pages/order/detail", "标题", "内容");
        assertEquals("pages/order/detail", body.get("page"));
    }
}
