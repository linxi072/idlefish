package com.idlefish.trade.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.item.dto.AuditResult;
import com.idlefish.trade.item.service.ContentAuditService;
import com.idlefish.trade.user.dto.WxSession;
import com.idlefish.trade.user.service.WechatLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 关键链路集成测试（真实 MySQL，回滚）：
 * - R-02 / RK-8 后台独立鉴权：错误密码拒绝；无 token 拒绝；普通用户令牌不得提权（403）。
 * - R-06 收藏：增/查/列表/取消闭环。
 * - R-01 金额单位：发布价以"分"入参，详情价以"元"展示（priceYuan = price/100）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTest {

    // 测试替身：沙箱无微信凭证，登录桩以「code 即 openid」（与既有 @MockBean 托管桩范式一致）
    @MockBean
    private WechatLoginService wechatLoginService;

    // 测试替身：机审走真实阿里云内容安全需 AK/SK + 外网，沙箱以「恒通过」桩替代
    @MockBean
    private ContentAuditService contentAuditService;

    /** 局部覆盖：机审「转人工复核」语义——商品发布后停留 pending_review，供后台审核流程用例使用。 */
    private void stubAuditManualReview() {
        when(contentAuditService.audit(any(), any(), any())).thenAnswer(inv -> {
            AuditResult r = new AuditResult();
            r.setPass(false);
            r.setSuggestion("review");
            r.setReason("转人工复核");
            return r;
        });
    }

    /** U01 状态机下 submitReview 直达终态（onsale/rejected），后台审核的 pending_review 入口以 SQL 直置。 */
    private void forcePendingReview(Long itemId) {
        jdbcTemplate.update("UPDATE t_item SET status = 'pending_review' WHERE id = ?", itemId);
    }

    @BeforeEach
    void stubWechatLogin() {
        when(wechatLoginService.code2Session(anyString())).thenAnswer(inv -> {
            WxSession s = new WxSession();
            s.setOpenid(inv.getArgument(0, String.class));
            return s;
        });
        when(contentAuditService.audit(any(), any(), any())).thenReturn(AuditResult.pass());
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode toJson(String body) throws Exception {
        return mapper.readTree(body);
    }

    private String userToken() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"test_openid_001\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return toJson(body).get("data").get("token").asText();
    }

    private Long publishItem(String token) throws Exception {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("categoryId", 1011);
        map.put("title", "测试二手手机");
        map.put("description", "九成新");
        map.put("price", 13800);
        map.put("images", java.util.Collections.singletonList("https://img.example/1.jpg"));
        String payload = mapper.writeValueAsString(map);
        String body = mvc.perform(post("/api/item/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return toJson(body).get("data").asLong();
    }

    // ===== R-02 / RK-8 后台独立鉴权 =====

    @Test
    void adminLoginWrongPasswordRejected() throws Exception {
        mvc.perform(post("/api/admin/auth/login")
                        .param("username", "admin").param("password", "bad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void adminLoginOkAndAccessProtected() throws Exception {
        String body = mvc.perform(post("/api/admin/auth/login")
                        .param("username", "admin").param("password", "admin123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String token = toJson(body).get("data").get("token").asText();

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void adminEndpointWithoutTokenRejected() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void regularUserCannotEscalateToAdmin() throws Exception {
        String userToken = userToken();
        // 普通用户令牌访问后台接口应被拒绝（FORBIDDEN），证明无法伪造 X-Admin-Role 提权
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.code").value(20003));
    }

    // ===== R-06 收藏闭环 =====

    @Test
    void favoriteToggleCheckAndList() throws Exception {
        String token = userToken();
        Long itemId = publishItem(token);

        // 收藏
        mvc.perform(post("/api/favorite/toggle")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        // 是否收藏 = true
        mvc.perform(get("/api/favorite/check").header("Authorization", "Bearer " + token)
                        .param("itemId", String.valueOf(itemId)))
                .andExpect(jsonPath("$.data").value(true));

        // 列表包含该商品
        mvc.perform(get("/api/favorite/list").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].itemId").value(itemId));

        // 取消收藏
        mvc.perform(post("/api/favorite/toggle")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + "}"))
                .andExpect(jsonPath("$.data").value(false));

        mvc.perform(get("/api/favorite/check").header("Authorization", "Bearer " + token)
                        .param("itemId", String.valueOf(itemId)))
                .andExpect(jsonPath("$.data").value(false));
    }

    // ===== R-01 金额单位契约 =====

    @Test
    void amountUnitFenToYuan() throws Exception {
        String token = userToken();
        Long itemId = publishItem(token);
        // 详情接口（公开）返回 priceYuan = price/100
        mvc.perform(get("/api/item/detail/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(13800))
                .andExpect(jsonPath("$.data.priceYuan").value(138.0));
    }

    // ===== T-1 / R-19：@Valid 生效，非法入参返回业务码 10002 =====

    @Test
    void orderCreateInvalidParamReturns10002() throws Exception {
        // 空 body 缺少 itemId / addressId，@Valid 应拦截（返回 10002 而非穿透到 Service）
        mvc.perform(post("/api/orders/create")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    void refundApplyInvalidParamReturns10002() throws Exception {
        mvc.perform(post("/api/refunds/apply")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
    }

    // ===== T-2 / R-16：后台列表 keyword 搜索与审计日志端点 =====

    private String adminToken() throws Exception {
        String body = mvc.perform(post("/api/admin/auth/login")
                        .param("username", "admin").param("password", "admin123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return toJson(body).get("data").get("token").asText();
    }

    @Test
    void adminItemSearchByKeywordAndStatus() throws Exception {
        stubAuditManualReview(); // 本用例需要 pending_review 状态商品
        String token = userToken();
        Long itemId = publishItem(token); // 标题含「测试二手手机」
        forcePendingReview(itemId);
        String admin = adminToken();

        // 关键字命中
        mvc.perform(get("/api/admin/items").header("Authorization", "Bearer " + admin)
                        .param("keyword", "测试二手手机"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));

        // 关键字不命中 -> 0 条（验证 keyword 真实下推）
        mvc.perform(get("/api/admin/items").header("Authorization", "Bearer " + admin)
                        .param("keyword", "绝对不存在的关键词ZZZ"))
                .andExpect(jsonPath("$.data.total").value(0));

        // 状态过滤命中（新发布商品处于 pending_review）
        mvc.perform(get("/api/admin/items").header("Authorization", "Bearer " + admin)
                        .param("status", "pending_review"))
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void adminAuditLogListAfterApprove() throws Exception {
        stubAuditManualReview(); // 本用例需要 pending_review 状态商品供后台审核
        String token = userToken();
        Long itemId = publishItem(token);
        forcePendingReview(itemId);
        String admin = adminToken();
        // 审核通过 -> 写审计日志
        mvc.perform(post("/api/admin/items/" + itemId + "/approve")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(0));
        // 审计日志列表接口（T-2 新增）应返回该条记录
        mvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[0].action").value("item:audit"));
    }

    @Test
    void adminUserSearchKeywordNarrows() throws Exception {
        String admin = adminToken();
        // 无关键字 -> 至少 1 条
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));
        // 不匹配关键字 -> 0 条（验证 keyword 真实下推）
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + admin)
                        .param("keyword", "绝对不存在ZZZ"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ===== R-08 / T-4：刷新令牌端点 + 管理员密码 PBKDF2 哈希 =====

    @Test
    void refreshTokenWorks() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"refresh_openid_777\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String refresh = toJson(body).get("data").get("refreshToken").asText();

        String r = mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String newToken = toJson(r).get("data").get("token").asText();
        org.junit.jupiter.api.Assertions.assertNotNull(newToken);
    }

    @Test
    void adminLoginPasswordHashMatches() throws Exception {
        // data-mysql.sql 中密码为 PBKDF2 加盐哈希（R-20 零外部依赖方案），明文 admin123 仍应登录成功（T-4）
        mvc.perform(post("/api/admin/auth/login")
                        .param("username", "admin").param("password", "admin123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
