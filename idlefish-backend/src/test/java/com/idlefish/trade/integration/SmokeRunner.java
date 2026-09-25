package com.idlefish.trade.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.web.DeviceContext;
import com.idlefish.trade.risk.entity.DeviceFingerprint;
import com.idlefish.trade.risk.mapper.DeviceFingerprintMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.enums.OrderStatus;
import com.idlefish.trade.common.enums.PayStatus;
import com.idlefish.trade.common.enums.RefundStatus;
import com.idlefish.trade.common.util.SimpleRateLimiter;
import com.idlefish.trade.trade.dto.RefundApplyDTO;
import com.idlefish.trade.trade.entity.DelayTask;
import com.idlefish.trade.trade.entity.FundFlow;
import com.idlefish.trade.trade.entity.Order;
import com.idlefish.trade.trade.entity.PayOrder;
import com.idlefish.trade.trade.entity.Refund;
import com.idlefish.trade.trade.entity.Settlement;
import com.idlefish.trade.trade.mapper.DelayTaskMapper;
import com.idlefish.trade.trade.mapper.FundFlowMapper;
import com.idlefish.trade.trade.mapper.OrderMapper;
import com.idlefish.trade.trade.mapper.PayOrderMapper;
import com.idlefish.trade.trade.mapper.RefundMapper;
import com.idlefish.trade.trade.mapper.SettlementMapper;
import com.idlefish.trade.notify.entity.Notification;
import com.idlefish.trade.notify.mapper.NotificationMapper;
import com.idlefish.trade.trade.service.RefundService;
import com.idlefish.trade.trade.service.SettlementService;
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
import java.time.LocalDateTime;
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
    private static SettlementService settlementService;
    private static RefundService refundService;
    private static OrderMapper orderMapper;
    private static RefundMapper refundMapper;
    private static SettlementMapper settlementMapper;
    private static FundFlowMapper fundFlowMapper;
    private static NotificationMapper notificationMapper;

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
            settlementService = ctx.getBean(SettlementService.class);
            refundService = ctx.getBean(RefundService.class);
            orderMapper = ctx.getBean(OrderMapper.class);
            refundMapper = ctx.getBean(RefundMapper.class);
            settlementMapper = ctx.getBean(SettlementMapper.class);
            fundFlowMapper = ctx.getBean(FundFlowMapper.class);
            notificationMapper = ctx.getBean(NotificationMapper.class);

            check("contextLoads", true);

            tradeEndToEnd();
            notificationCheck();
            contentAudit();
            fileUpload();
            wechatLoginCheck();
            coreDomainCheck();
            notifyCloseLoopCheck();
            rateLimiterCheck();
            systemManageCheck();

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
        // 生产已移除 Mock 支付：需真实微信 v3 回调（/api/pay/notify/v3）或本地以桩放行验签后走 /api/pay/notify
        mvc.perform(post("/api/pay/notify").param("payNo", po.getPayNo())
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

    /**
     * F-05 事件通知闭环校验：交易链路触发了支付/确认收货/结算三类通知（买卖双向 + 结算触达）。
     * 当前冒烟用户同时是买卖双方，故按通知 type 计数断言（fresh MySQL，仅冒烟数据）。
     */
    private static void notifyCloseLoopCheck() {
        System.out.println("[notifyCloseLoopCheck]");
        long paid = notificationMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>()
                        .eq(Notification::getType, "order_paid"));
        check("notify order_paid emitted", paid > 0);
        long confirmed = notificationMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>()
                        .eq(Notification::getType, "order_confirmed"));
        check("notify order_confirmed emitted", confirmed > 0);
        long settled = notificationMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>()
                        .eq(Notification::getType, "settlement_success"));
        check("notify settlement_success emitted", settled > 0);
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

    /**
     * 核心交易域不变量离线校验（与 CoreDomainTest 同源，供沙箱离线门禁）。
     * 覆盖：结算幂等+金额换算、T+1 放款、退款状态机+状态守卫、乐观锁 CAS。
     */
    private static void coreDomainCheck() {
        System.out.println("[coreDomainCheck]");

        // 1) 结算幂等 + 金额换算（5% 佣金）
        String orderNo = "NO_DOM_" + System.nanoTime();
        long payAmount = 10000L;
        insertPaidOrder(orderNo, "PAY_DOM_" + System.nanoTime(), payAmount);
        settlementService.onTradeSuccess(orderNo);
        settlementService.onTradeSuccess(orderNo); // 幂等第二次
        java.util.List<Settlement> settles = settlementMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Settlement>()
                        .eq(Settlement::getOrderNo, orderNo));
        check("settlement idempotent (1 row)", settles.size() == 1);
        Settlement s = settles.get(0);
        check("settlement fee = 5% (500)", s.getPlatformFee() != null && s.getPlatformFee() == 500L);
        check("settlement seller amount = 9500", s.getAmount() != null && s.getAmount() == 9500L);

        // 2) T+1 到期放款 + 卖家入账流水
        Settlement dueUpd = new Settlement();
        dueUpd.setId(s.getId());
        dueUpd.setSettleAt(LocalDateTime.now().minusMinutes(1));
        settlementMapper.updateById(dueUpd);
        settlementService.processDue();
        Settlement after = settlementMapper.selectById(s.getId());
        check("settlement due -> settled", after != null && "settled".equals(after.getStatus()));
        FundFlow ff = fundFlowMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FundFlow>()
                        .eq(FundFlow::getBizNo, after.getSettleNo()));
        check("settlement generates seller IN flow", ff != null && "IN".equals(ff.getDirection()));

        // 3) 退款状态机：apply → agree → refunded + 关单
        String roNo = "NO_RF_" + System.nanoTime();
        String roPay = "PAY_RF_" + System.nanoTime();
        insertPaidOrder(roNo, roPay, 5000L);
        insertPayOrder(roPay, roNo, 5000L);
        RefundApplyDTO dto = new RefundApplyDTO();
        dto.setOrderNo(roNo);
        dto.setType("only_refund");
        dto.setAmount(5000L);
        String refundNo = refundService.apply(1001L, dto);
        Refund r1 = refundMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Refund>()
                        .eq(Refund::getRefundNo, refundNo));
        check("refund apply -> wait_seller", r1 != null && RefundStatus.WAIT_SELLER.getCode().equals(r1.getStatus()));
        refundService.agree(2001L, refundNo);
        Refund r2 = refundMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Refund>()
                        .eq(Refund::getRefundNo, refundNo));
        check("refund agree -> refunded", r2 != null && RefundStatus.REFUNDED.getCode().equals(r2.getStatus()));
        Order oClosed = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, roNo));
        check("refunded order -> closed", oClosed != null && OrderStatus.CLOSED.getCode().equals(oClosed.getStatus()));

        // 4) 退款状态守卫：rejected 后不可 agree/cancel
        String roNo2 = "NO_RF2_" + System.nanoTime();
        String roPay2 = "PAY_RF2_" + System.nanoTime();
        insertPaidOrder(roNo2, roPay2, 5000L);
        insertPayOrder(roPay2, roNo2, 5000L);
        RefundApplyDTO dto2 = new RefundApplyDTO();
        dto2.setOrderNo(roNo2);
        dto2.setType("only_refund");
        dto2.setAmount(5000L);
        String refundNo2 = refundService.apply(1001L, dto2);
        refundService.reject(2001L, refundNo2, "不想要了");
        boolean guard1 = false, guard2 = false;
        try {
            refundService.agree(2001L, refundNo2);
        } catch (BizException e) {
            guard1 = (e.getCode() == Code.STATE_NOT_ALLOWED.getCode());
        }
        try {
            refundService.cancel(1001L, refundNo2);
        } catch (BizException e) {
            guard2 = (e.getCode() == Code.STATE_NOT_ALLOWED.getCode());
        }
        check("refund guard: rejected cannot agree", guard1);
        check("refund guard: rejected cannot cancel", guard2);

        // 5) 乐观锁 CAS：过期版本号更新被拦截
        Order o = new Order();
        o.setOrderNo("NO_CAS_" + System.nanoTime());
        o.setBuyerId(1001L); o.setSellerId(2001L); o.setItemId(3001L);
        o.setPayAmount(1000L); o.setPayNo("PAY_CAS_" + System.nanoTime());
        o.setStatus(OrderStatus.PAID.getCode());
        o.setVersion(0);
        orderMapper.insert(o);
        Order upd1 = new Order();
        upd1.setId(o.getId()); upd1.setVersion(0); upd1.setStatus(OrderStatus.SHIPPING.getCode());
        int a1 = orderMapper.updateById(upd1);
        check("cas first update ok", a1 == 1);
        Order upd2 = new Order();
        upd2.setId(o.getId()); upd2.setVersion(0); upd2.setStatus(OrderStatus.COMPLETED.getCode());
        int a2 = orderMapper.updateById(upd2);
        check("cas stale version blocked", a2 == 0);
    }

    /** 限流骨架离线校验（纯逻辑，不依赖 Spring）。 */
    private static void rateLimiterCheck() {
        System.out.println("[rateLimiterCheck]");
        SimpleRateLimiter limiter = new SimpleRateLimiter(3, 60);
        check("ratelimit allow 1", limiter.tryAcquire("k"));
        check("ratelimit allow 2", limiter.tryAcquire("k"));
        check("ratelimit allow 3", limiter.tryAcquire("k"));
        check("ratelimit used=3", limiter.used("k") == 3L);
        check("ratelimit reject 4th", !limiter.tryAcquire("k"));
        // 不同 key 隔离
        SimpleRateLimiter limiter2 = new SimpleRateLimiter(1, 60);
        check("ratelimit key isolation a", limiter2.tryAcquire("a"));
        check("ratelimit key isolation a-full", !limiter2.tryAcquire("a"));
        check("ratelimit key isolation b", limiter2.tryAcquire("b"));
    }

    private static void insertPaidOrder(String orderNo, String payNo, long payAmount) {
        Order o = new Order();
        o.setOrderNo(orderNo);
        o.setBuyerId(1001L); o.setSellerId(2001L); o.setItemId(3001L);
        o.setPayAmount(payAmount);
        o.setPayNo(payNo);
        o.setStatus(OrderStatus.PAID.getCode());
        o.setVersion(0);
        orderMapper.insert(o);
    }

    private static void insertPayOrder(String payNo, String orderNo, long amount) {
        PayOrder po = new PayOrder();
        po.setPayNo(payNo);
        po.setOrderNo(orderNo);
        po.setBuyerId(1001L);
        po.setAmount(amount);
        po.setChannel("wechat");
        po.setStatus(PayStatus.WAIT.getCode());
        payOrderMapper.insert(po);
    }

    /**
     * PC 系统管理离线校验（走 MockMvc，覆盖 AdminAuth 拦截器 + DB 权限 + 字典缓存）：
     * 菜单树 / 用户列表 / 角色列表 / 机构树 / 字典下拉 五个核心读取均返回 200 且有数据。
     */
    private static void systemManageCheck() throws Exception {
        System.out.println("[systemManageCheck]");
        String at = adminToken();
        String ah = "Bearer " + at;

        MvcResult mt = mvc.perform(get("/api/admin/system/menu/tree").header("Authorization", ah))
                .andExpect(status().isOk()).andReturn();
        JsonNode menus = MAPPER.readTree(mt.getResponse().getContentAsString()).get("data");
        check("sys menu tree non-empty", menus.isArray() && menus.size() >= 1);

        MvcResult ut = mvc.perform(get("/api/admin/system/user").param("keyword", "admin").header("Authorization", ah))
                .andExpect(status().isOk()).andReturn();
        JsonNode users = MAPPER.readTree(ut.getResponse().getContentAsString()).get("data");
        check("sys user list has admin", users != null && users.get("records").isArray() && users.get("records").size() >= 1);

        MvcResult rt = mvc.perform(get("/api/admin/system/role").header("Authorization", ah))
                .andExpect(status().isOk()).andReturn();
        JsonNode roles = MAPPER.readTree(rt.getResponse().getContentAsString()).get("data");
        check("sys role list non-empty", roles != null && roles.get("records").isArray() && roles.get("records").size() >= 1);

        MvcResult ot = mvc.perform(get("/api/admin/system/organization/tree").header("Authorization", ah))
                .andExpect(status().isOk()).andReturn();
        JsonNode orgs = MAPPER.readTree(ot.getResponse().getContentAsString()).get("data");
        check("sys org tree non-empty", orgs.isArray() && orgs.size() >= 1);

        MvcResult dt = mvc.perform(get("/api/admin/system/dict/dropdown").param("dictType", "sys_user_status")
                        .header("Authorization", ah)).andExpect(status().isOk()).andReturn();
        JsonNode dict = MAPPER.readTree(dt.getResponse().getContentAsString()).get("data");
        check("sys dict dropdown 2 items", dict.isArray() && dict.size() == 2);
    }
}
