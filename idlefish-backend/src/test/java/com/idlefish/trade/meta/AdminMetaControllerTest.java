package com.idlefish.trade.meta;

import com.idlefish.trade.meta.controller.AdminMetaController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminMetaController 接口测试：使用 standalone MockMvc（无 Spring 上下文、无 MySQL），
 * 仅校验 handler 的序列化与路由，离线可跑。
 */
class AdminMetaControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminMetaController()).build();
    }

    @Test
    void statusDict_returns_all_groups() throws Exception {
        mvc.perform(get("/api/admin/meta/status-dict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].domain").value("order"))
                .andExpect(jsonPath("$.data[0].items[0].code").value("pending_pay"))
                .andExpect(jsonPath("$.data[0].items[0].color").exists())
                .andExpect(jsonPath("$.data[0].items[0].name").exists());
    }

    @Test
    void statusDictByDomain_ok() throws Exception {
        mvc.perform(get("/api/admin/meta/status-dict/dispute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.domain").value("dispute"))
                .andExpect(jsonPath("$.data.items.length()").value(6));
    }

    @Test
    void terms_returns_glossary() throws Exception {
        mvc.perform(get("/api/admin/meta/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].term").exists())
                .andExpect(jsonPath("$.data[0].definition").exists())
                .andExpect(jsonPath("$.data[0].synonym").exists())
                .andExpect(jsonPath("$.data[0].domain").exists());
    }

    @Test
    void designTokens_returns_tokens_with_multi_platform_mapping() throws Exception {
        mvc.perform(get("/api/admin/meta/design-tokens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].token").exists())
                .andExpect(jsonPath("$.data[0].value").exists())
                .andExpect(jsonPath("$.data[0].platforms.web").exists())
                .andExpect(jsonPath("$.data[0].platforms.mini").exists())
                .andExpect(jsonPath("$.data[0].usage").exists());
    }
}
