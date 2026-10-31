package com.idlefish.trade.web;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdminArgumentResolver;
import com.idlefish.trade.common.web.CurrentUserArgumentResolver;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.compliance.controller.AdminComplianceController;
import com.idlefish.trade.compliance.controller.ComplianceController;
import com.idlefish.trade.compliance.service.ComplianceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ITER-U05 合规端点 {@code @Valid} 接线验证（standalone MockMvc + 真实解析器 + LocalValidatorFactoryBean，离线可跑）。
 *
 * <p>授权项编码与偏好开关值均为业务必填：空串/缺失由校验拦截返回 400；合法载荷放行并委托服务（code=0）。
 * standalone 不加载 {@code @RestControllerAdvice}，故 400 即拦截铁证。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ControllerValidationWiringComplianceTest {

    @Mock private ComplianceService complianceService;

    private final LoginUser user = user();
    private final AdminUser admin = admin();

    private MockMvc userMvc;
    private MockMvc adminMvc;

    private LoginUser user() {
        LoginUser u = new LoginUser();
        u.setUserId(1L);
        return u;
    }

    private AdminUser admin() {
        AdminUser a = new AdminUser();
        a.setId(1L);
        return a;
    }

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        userMvc = MockMvcBuilders.standaloneSetup(new ComplianceController(complianceService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
        adminMvc = MockMvcBuilders.standaloneSetup(new AdminComplianceController(complianceService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();
    }

    // ===== 授权项编码必填 =====
    @Test
    void grant_without_consent_code_rejected() throws Exception {
        userMvc.perform(post("/api/compliance/consent/grant")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentCode\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void grant_valid_passes() throws Exception {
        userMvc.perform(post("/api/compliance/consent/grant")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentCode\":\"phone\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(complianceService).grantConsent(eq(1L), eq("phone"));
    }

    @Test
    void revoke_without_consent_code_rejected() throws Exception {
        userMvc.perform(post("/api/compliance/consent/revoke")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revoke_valid_passes() throws Exception {
        userMvc.perform(post("/api/compliance/consent/revoke")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentCode\":\"marketing_push\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(complianceService).revokeConsent(eq(1L), eq("marketing_push"));
    }

    // ===== 偏好开关值必填 =====
    @Test
    void marketing_notify_without_enabled_rejected() throws Exception {
        userMvc.perform(post("/api/compliance/notify/marketing")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void marketing_notify_valid_passes() throws Exception {
        userMvc.perform(post("/api/compliance/notify/marketing")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(complianceService).setMarketingNotify(eq(1L), eq(false));
    }

    @Test
    void care_mode_without_enabled_rejected() throws Exception {
        userMvc.perform(post("/api/compliance/care-mode")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void care_mode_valid_passes() throws Exception {
        userMvc.perform(post("/api/compliance/care-mode")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(complianceService).setCareMode(eq(1L), eq(true));
    }

    // ===== 后台端点 =====
    @Test
    void admin_mark_minor_passes() throws Exception {
        adminMvc.perform(post("/api/admin/compliance/minor/mark")
                        .requestAttr("adminUser", admin)
                        .param("userId", "2")
                        .param("age", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(complianceService).markMinor(eq(2L), eq(15));
    }

    @Test
    void admin_profile_passes() throws Exception {
        adminMvc.perform(get("/api/admin/compliance/profile")
                        .requestAttr("adminUser", admin)
                        .param("userId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(complianceService).profile(anyLong());
    }

    @Test
    void admin_consent_items_passes() throws Exception {
        adminMvc.perform(get("/api/admin/compliance/consent-items")
                        .requestAttr("adminUser", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    void user_profile_passes() throws Exception {
        userMvc.perform(get("/api/compliance/profile")
                        .requestAttr("loginUser", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(complianceService).profile(anyLong());
    }
}
