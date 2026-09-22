package com.idlefish.trade.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 关键链路集成测试（真实 H2，回滚）：
 * - R-02 / RK-8 后台独立鉴权：错误密码拒绝；无 token 拒绝；普通用户令牌不得提权（403）。
 * - R-06 收藏：增/查/列表/取消闭环。
 * - R-01 金额单位：发布价以"分"入参，详情价以"元"展示（priceYuan = price/100）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
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
}
