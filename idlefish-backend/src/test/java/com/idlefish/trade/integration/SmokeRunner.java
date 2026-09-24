package com.idlefish.trade.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.web.DeviceContext;
import com.idlefish.trade.risk.entity.DeviceFingerprint;
import com.idlefish.trade.risk.mapper.DeviceFingerprintMapper;
import com.idlefish.trade.trade.entity.DelayTask;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.mapper.DelayTaskMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.user.entity.User;
import com.idlefish.trade.user.mapper.UserMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 离线冒烟运行器（非 JUnit，免 surefire）：直接启动 Spring 上下文并通过 MockMvc 跑通七大组件集成链路。
 * 用于沙箱离线环境验证 Bean 装配与端到端闭环；正式回归请用 {@link SubsystemSmokeTest}。
 */
public class SmokeRunner {

    private static final String DEVICE_ID = "dev-smoke-001";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static int failures = 0;
    private static MockMvc mvc;
    private static String bearer;
    private static DelayTaskMapper delayTaskMapper;
    private static DeviceFingerprintMapper deviceFingerprintMapper;
    private static PayOrderMapper payOrderMapper;
    private static UserMapper userMapper;

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext ctx = SpringApplication.run(
                com.idlefish.trade.TradeApplication.class,
                new String[]{"--server.port=-1"});
        try {
            WebApplicationContext wac = (WebApplicationContext) ctx;
            mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(wac).build();
            delayTaskMapper = ctx.getBean(DelayTaskMapper.class);
            deviceFingerprintMapper = ctx.getBean(DeviceFingerprintMapper.class);
            payOrderMapper = ctx.getBean(PayOrderMapper.class);
            userMapper = ctx.getBean(UserMapper.class);

            check("contextLoads", true);

            tradeEndToEnd();
            notificationCheck();
            contentAudit();
            fileUpload();
            wechatLoginCheck();

            System.out.println("\n==== SMOKE RESULT: " + (failures == 0 ? "ALL PASS" : failures + " FAILURE(S)") + " ====");
        } catch (Throwable t) {
            failures++;
            System.err.println("SMOKE EXCEPTION: " + t);
            t.printStackTrace();
        } finally {
            SpringApplication.exit(ctx);
        }
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(String name, boolean cond) {
        if (cond) {
            System.out.println("  [PASS] " + name);
        } else {
            failures++;
            System.out.println("  [FAIL] " + name);
        }
    }

    private static MockHttpServletRequestBuilder withDevice(MockHttpServletRequestBuilder b) {
        return b.header("X-Device-Id", DEVICE_ID)
                .header("User-Agent", "smoke/1.0")
                .header("X-Forwarded-For", "127.0.0.1");
    }

    private static String login(String code) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return MAPPER.readTree(r.getResponse().getContentAsString()).get("data").get("token").asText();
    }

    private static String adminToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/admin/auth/login")
                        .param("username", "admin").param("password", "admin123"))
                .andExpect(status().isOk()).andReturn();
        return MAPPER.readTree(r.getResponse().getContentAsString()).get("data").get("token").asText();
    }

    private static Long publishItem(String token, String title, String desc) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categoryId", 1011);
        m.put("title", title);
        m.put("description", desc);
        m.put("price", 13800);
        m.put("images", java.util.Collections.singletonList("https://img.example/1.jpg"));
        String payload = MAPPER.writeValueAsString(m);
        MvcResult r = mvc.perform(withDevice(post("/api/item/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(payload)))
                .andExpect(status().isOk()).andReturn();
        return MAPPER.readTree(r.getResponse().getContentAsString()).get("data").asLong();
    }

    private static void submitReview(String token, Long itemId) throws Exception {
        mvc.perform(withDevice(post("/api/item/" + itemId + "/submit")
                        .header("Authorization", "Bearer " + token)))
                .andExpect(status().isOk());
    }

    private static Long createAddress(String token) throws Exception {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("receiverName", "张三");
        a.put("phone", "13800000000");
        a.put("province", "浙江省");
        a.put("city", "杭州市");
        a.put("district", "西湖区");
        a.put("detail", "文三路 100 号");
        a.put("isDefault", 1);
        MvcResult r = mvc.perform(post("/api/address")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(a)))
                .andExpect(status().isOk()).andReturn();
        return MAPPER.readTree(r.getResponse().getContentAsString()).get("data").asLong();
    }

    private static String createOrder(String token, Long itemId, Long addressId) throws Exception {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("itemId", itemId);
        o.put("quantity", 1);
        o.put("addressId", addressId);
        String payload = MAPPER.writeValueAsString(o);
        MvcResult r = mvc.perform(withDevice(post("/api/orders/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)))
                .andExpect(status().isOk()).andReturn();
        return MAPPER.readTree(r.getResponse().getContentAsString()).get("data").get("orderNo").asText();
    }

    private static String orderStatus(String orderNo) throws Exception {
        MvcResult r = mvc.perform(get("/api/orders/" + orderNo)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = MAPPER.readTree(r.getResponse().getContentAsString());
        return node.get("data").get("status").asText();
    }

    private static String itemStatus(Long itemId) throws Exception {
        MvcResult r = mvc.perform(get("/api/item/detail/" + itemId))
                .andExpect(status().isOk()).andReturn();
        return MAPPER.readTree(r.getResponse().getContentAsString()).get("data").get("status").asText();
    }

    private static void assertDelayTask(String type, String bizId) {
        DelayTask t = delayTaskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DelayTask>()
                        .eq(DelayTask::getTaskType, type).eq(DelayTask::getBizId, bizId));
        check("delayTask " + type, t != null);
    }

    private static void tradeEndToEnd() throws Exception {
        System.out.println("[tradeEndToEnd]");
        String token = login("smoke_buyer_trade");
        bearer = "Bearer " + token;
        Long itemId = publishItem(token, "二手 佳能 相机 95新", "快门少，附原装包");
        submitReview(token, itemId);
        check("item onsale after submit", "onsale".equals(itemStatus(itemId)));

        Long addressId = createAddress(token);
        String orderNo = createOrder(token, itemId, addressId);
        assertDelayTask("ORDER_CLOSE", orderNo);

        long fp = deviceFingerprintMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeviceFingerprint>()
                        .eq(DeviceFingerprint::getDeviceId, DEVICE_ID));
        check("device fingerprint recorded", fp >= 1);

        PayOrder po = payOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayOrder>()
                        .eq(PayOrder::getOrderNo, orderNo));
        check("payOrder created", po != null);
        mvc.perform(post("/api/pay/mock/" + po.getPayNo())
                        .header("Authorization", bearer)).andExpect(status().isOk());
        check("order paid", "paid".equals(orderStatus(orderNo)));
        assertDelayTask("REMIND_SHIP", orderNo);

        mvc.perform(withDevice(post("/api/orders/" + orderNo + "/ship")
                        .header("Authorization", "Bearer " + token)
                        .param("logisticsNo", "SF1234567890")))
                .andExpect(status().isOk());
        check("order shipping", "shipping".equals(orderStatus(orderNo)));
        assertDelayTask("ORDER_CONFIRM", orderNo);

        MvcResult tr = mvc.perform(get("/api/orders/" + orderNo + "/logistics")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        JsonNode track = MAPPER.readTree(tr.getResponse().getContentAsString()).get("data");
        check("logistics track non-empty", track.isArray() && track.size() >= 1);

        mvc.perform(withDevice(post("/api/orders/" + orderNo + "/confirm")
                        .header("Authorization", "Bearer " + token)))
                .andExpect(status().isOk());
        check("order completed", "completed".equals(orderStatus(orderNo)));
    }

    private static void notificationCheck() throws Exception {
        System.out.println("[notificationCheck]");
        // 交易链路（支付/发货/确认收货/审核）应已触发多条站内通知
        MvcResult r = mvc.perform(get("/api/notify/unread-count")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        long unread = MAPPER.readTree(r.getResponse().getContentAsString()).get("data").asLong();
        check("notify unread > 0", unread > 0);

        MvcResult l = mvc.perform(get("/api/notify/list")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = MAPPER.readTree(l.getResponse().getContentAsString()).get("data");
        check("notify list has records", data.get("records").isArray() && data.get("records").size() > 0);

        // 标记全部已读后未读应为 0（验证已读写回）
        mvc.perform(post("/api/notify/read-all")
                        .header("Authorization", bearer)).andExpect(status().isOk());
        MvcResult u2 = mvc.perform(get("/api/notify/unread-count")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        long after = MAPPER.readTree(u2.getResponse().getContentAsString()).get("data").asLong();
        check("notify all-read -> unread 0", after == 0);
    }

    private static void contentAudit() throws Exception {
        System.out.println("[contentAudit]");
        String token = login("smoke_buyer_audit");
        Long banned = publishItem(token, "代开发票便宜出", "低价代开");
        submitReview(token, banned);
        check("banned word -> rejected", "rejected".equals(itemStatus(banned)));

        String token2 = login("smoke_buyer_clean");
        Long clean = publishItem(token2, "九成新 iPhone 自用转让", "无划痕");
        submitReview(token2, clean);
        check("clean item -> onsale", "onsale".equals(itemStatus(clean)));
    }

    private static void fileUpload() throws Exception {
        System.out.println("[fileUpload]");
        String token = login("smoke_buyer_file");
        MockMultipartFile file = new MockMultipartFile("file", "smoke.txt",
                "text/plain", "idlefish-smoke".getBytes());
        MvcResult r = mvc.perform(multipart("/api/file/upload")
                        .file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        String url = MAPPER.readTree(r.getResponse().getContentAsString()).get("data").asText();
        check("url startsWith /uploads/", url.startsWith("/uploads/"));
        String name = url.substring(url.lastIndexOf('/') + 1);
        Path target = Paths.get(System.getProperty("user.dir"), "uploads", name);
        check("file on disk", Files.exists(target));
        try { Files.deleteIfExists(target); } catch (Exception ignored) { }
    }

    private static void wechatLoginCheck() throws Exception {
        System.out.println("[wechatLoginCheck]");
        // 验证登录经由新的 WechatLoginService 抽象（Mock 模式：openid == code）且用户落库、幂等复用
        String code = "wx_login_check_" + System.nanoTime();
        String token = login(code);
        check("wx login returns token", token != null && !token.isBlank());

        User u = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getWxOpenid, code));
        check("mock openid == code persisted", u != null && code.equals(u.getWxOpenid()));

        // 相同 code 二次登录应复用同一用户（loadOrCreateByOpenid 幂等）
        login(code);
        User again = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getWxOpenid, code));
        check("login idempotent (same user)", again != null && again.getId().equals(u.getId()));
    }
}
