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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 七大外部组件子系统「集成联调」冒烟测试（默认 mock 模式，零外部依赖即可跑通）：
 * - B3/E3 AI 内容审核：发布含敏感词商品 → 机审驳回（rejected）；正常商品 → 审核通过（onsale）。
 * - F3 设备指纹风控：下单携带设备头 → 落设备指纹 + 埋点；风控引擎 R3/R4/R5/R6 规则可加载。
 * - D6 RocketMQ 延时队列（本地实现）：下单提交 ORDER_CLOSE、支付成功提交 REMIND_SHIP、发货提交 ORDER_CONFIRM。
 * - D5 物流 API（本地模拟）：发货落 t_logistics，轨迹查询返回非空。
 * - D2 微信支付+分账（Mock）：支付成功幂等落地 + 状态流转（paid→shipping→completed）。
 * - OSS 对象存储（本地磁盘实现）：文件上传落盘 uploads/ 并返回可访问 URL。
 * - ES 索引（本地实现）：审核通过触发 indexItem（mock 无副作用，验证调用链不抛错）。
 * 验证核心：所有 Bean 正常装配（无循环依赖/缺失 Bean），关键子系统端到端闭环。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubsystemSmokeTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DelayTaskMapper delayTaskMapper;
    @Autowired
    private DeviceFingerprintMapper deviceFingerprintMapper;
    @Autowired
    private PayOrderMapper payOrderMapper;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String DEVICE_ID = "dev-smoke-001";

    private MockHttpServletRequestBuilder withDevice(MockHttpServletRequestBuilder b) {
        return b.header("X-Device-Id", DEVICE_ID)
                .header("User-Agent", "smoke/1.0")
                .header("X-Forwarded-For", "127.0.0.1");
    }

    private String login(String code) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("data").get("token").asText();
    }

    private String adminToken() throws Exception {
        String body = mvc.perform(post("/api/admin/auth/login")
                        .param("username", "admin").param("password", "admin123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("data").get("token").asText();
    }

    private Long publishItem(String token, String title, String desc) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("categoryId", 1011);
        map.put("title", title);
        map.put("description", desc);
        map.put("price", 13800);
        map.put("images", java.util.Collections.singletonList("https://img.example/1.jpg"));
        String payload = mapper.writeValueAsString(map);
        String body = mvc.perform(withDevice(post("/api/item/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("data").asLong();
    }

    private void submitReview(String token, Long itemId) throws Exception {
        mvc.perform(withDevice(post("/api/item/" + itemId + "/submit")
                        .header("Authorization", "Bearer " + token)))
                .andExpect(status().isOk());
    }

    private Long createAddress(String token) throws Exception {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("receiverName", "张三");
        a.put("phone", "13800000000");
        a.put("province", "浙江省");
        a.put("city", "杭州市");
        a.put("district", "西湖区");
        a.put("detail", "文三路 100 号");
        a.put("isDefault", 1);
        String body = mvc.perform(post("/api/address")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(a)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("data").asLong();
    }

    private String createOrder(String token, Long itemId, Long addressId) throws Exception {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("itemId", itemId);
        o.put("quantity", 1);
        o.put("addressId", addressId);
        String body = mvc.perform(withDevice(post("/api/orders/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(o))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("data").get("orderNo").asText();
    }

    private String orderStatus(String orderNo) throws Exception {
        String body = mvc.perform(get("/api/orders/" + orderNo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("data").get("status").asText();
    }

    private void assertDelayTask(String type, String bizId) {
        DelayTask t = delayTaskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DelayTask>()
                        .eq(DelayTask::getTaskType, type)
                        .eq(DelayTask::getBizId, bizId));
        assertNotNull(t, "应存在延时任务 type=" + type + " bizId=" + bizId);
    }

    // ===== 上下文装配：所有新增子系统 Bean 正常加载（无循环依赖/缺失） =====

    @Test
    void contextLoads() {
        // @SpringBootTest 已在此前完成上下文装配；到达此处即证明全部 Bean 装配成功
        assertNotNull(mvc);
    }

    // ===== B3/E3 内容审核：敏感词拦截 + 正常通过 =====

    @Test
    void contentAuditRejectsBannedWord() throws Exception {
        String token = login("smoke_buyer_audit");
        Long itemId = publishItem(token, "代开发票便宜出", "低价代开");
        submitReview(token, itemId);
        String status = orderStatus2Item(itemId);
        assertEquals("rejected", status, "含敏感词商品应被机审驳回");
    }

    @Test
    void contentAuditPassesCleanItem() throws Exception {
        String token = login("smoke_buyer_clean");
        Long itemId = publishItem(token, "九成新 iPhone 自用转让", "无划痕，配件齐全");
        submitReview(token, itemId);
        assertEquals("onsale", orderStatus2Item(itemId), "正常商品审核应通过并在售");
    }

    private String orderStatus2Item(Long itemId) throws Exception {
        String body = mvc.perform(get("/api/item/detail/" + itemId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("data").get("status").asText();
    }

    // ===== F3 设备指纹 + D6 延时队列 + D5 物流 + D2 支付 端到端闭环 =====

    @Test
    void tradeSubsystemEndToEnd() throws Exception {
        String token = login("smoke_buyer_trade");
        Long itemId = publishItem(token, "二手 佳能 相机 95新", "快门少，附原装包");
        submitReview(token, itemId); // → onsale（触发 ES 索引 mock + 机审）

        Long addressId = createAddress(token);
        String orderNo = createOrder(token, itemId, addressId);

        // D6：下单应提交 ORDER_CLOSE 延时任务
        assertDelayTask("ORDER_CLOSE", orderNo);

        // F3：下单携带设备头 → 落设备指纹
        long fp = deviceFingerprintMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeviceFingerprint>()
                        .eq(DeviceFingerprint::getDeviceId, DEVICE_ID));
        assertTrue(fp >= 1, "应记录设备指纹（deviceId=" + DEVICE_ID + "）");

        // D2：Mock 支付成功（幂等落地 → paid），并触发 REMIND_SHIP 延时任务
        PayOrder po = payOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayOrder>()
                        .eq(PayOrder::getOrderNo, orderNo));
        assertNotNull(po);
        mvc.perform(post("/api/pay/mock/" + po.getPayNo())).andExpect(status().isOk());
        assertEquals("paid", orderStatus(orderNo), "支付后订单应为 paid");
        assertDelayTask("REMIND_SHIP", orderNo);

        // 发货：D6 提交 ORDER_CONFIRM；D5 落物流
        mvc.perform(withDevice(post("/api/orders/" + orderNo + "/ship")
                        .header("Authorization", "Bearer " + token)
                        .param("logisticsNo", "SF1234567890")))
                .andExpect(status().isOk());
        assertEquals("shipping", orderStatus(orderNo), "发货后订单应为 shipping");
        assertDelayTask("ORDER_CONFIRM", orderNo);

        // D5：物流轨迹查询返回非空
        String trackBody = mvc.perform(get("/api/orders/" + orderNo + "/logistics"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode track = mapper.readTree(trackBody).get("data");
        assertTrue(track.isArray() && track.size() >= 1, "物流轨迹应非空");

        // 确认收货：D2 结算（mock 跳过真实分账）→ completed
        mvc.perform(withDevice(post("/api/orders/" + orderNo + "/confirm")
                        .header("Authorization", "Bearer " + token)))
                .andExpect(status().isOk());
        assertEquals("completed", orderStatus(orderNo), "确认收货后订单应为 completed");
    }

    // ===== OSS 对象存储（本地磁盘实现，file.mock=false） =====

    @Test
    void fileUploadStoresLocally() throws Exception {
        String token = login("smoke_buyer_file");
        MockMultipartFile file = new MockMultipartFile("file", "smoke.txt",
                "text/plain", "idlefish-smoke".getBytes());
        String body = mvc.perform(multipart("/api/file/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = mapper.readTree(body).get("data").asText();
        assertTrue(url.startsWith("/uploads/"), "返回 URL 应指向 uploads 前缀: " + url);

        String name = url.substring(url.lastIndexOf('/') + 1);
        Path target = Paths.get(System.getProperty("user.dir"), "uploads", name);
        assertTrue(Files.exists(target), "文件应落盘到 uploads/");
        // 清理冒烟产物，避免污染工作区
        try { Files.deleteIfExists(target); } catch (Exception ignored) { }
    }
}
