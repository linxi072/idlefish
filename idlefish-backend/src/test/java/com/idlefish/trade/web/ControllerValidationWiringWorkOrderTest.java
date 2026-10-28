package com.idlefish.trade.web;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdminArgumentResolver;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.web.CurrentUserArgumentResolver;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.workorder.controller.AdminWorkOrderController;
import com.idlefish.trade.workorder.controller.WorkOrderController;
import com.idlefish.trade.workorder.service.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-12 客服工单 @Valid 接线验证（standalone MockMvc + 真实 CurrentUser/CurrentAdmin 解析器 + LocalValidatorFactoryBean，离线可跑）。
 *
 * <p>新写端点默认带 {@code @Valid}：非法载荷（缺必填字段 / 满意度越界）由校验拦截返回 400；
 * 合法载荷放行并委托服务（返回 SUCCESS=0）。standalone 不加载 @RestControllerAdvice，故 400 即拦截铁证。
 */
@ExtendWith(MockitoExtension.class)
class ControllerValidationWiringWorkOrderTest {

    @Mock private WorkOrderService workOrderService;

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
        userMvc = MockMvcBuilders.standaloneSetup(new WorkOrderController(workOrderService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
        adminMvc = MockMvcBuilders.standaloneSetup(new AdminWorkOrderController(workOrderService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();
    }

    // ===== create：subject/description/type/priority/channel 必填 =====
    @Test
    void create_invalid_rejected() throws Exception {
        userMvc.perform(post("/api/workorders/create")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"\",\"description\":\"\",\"type\":null,\"priority\":null,\"channel\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_valid_passes() throws Exception {
        userMvc.perform(post("/api/workorders/create")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"退款未到账\",\"description\":\"下单后未收到退款\",\"type\":\"REFUND\",\"priority\":\"HIGH\",\"channel\":\"APP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(workOrderService).create(eq(1L), eq("REFUND"), eq("HIGH"), eq("APP"),
                anyString(), anyString(), any());
    }

    // ===== close（满意度回访）：satisfaction 1-5 必填 =====
    @Test
    void close_invalid_rejected() throws Exception {
        userMvc.perform(post("/api/workorders/close?id=1")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"satisfaction\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void close_valid_passes() throws Exception {
        userMvc.perform(post("/api/workorders/close?id=1")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"satisfaction\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(workOrderService).closeByUser(1L, 1L, 5);
    }

    // ===== admin assign：assigneeId 必填 =====
    @Test
    void admin_assign_invalid_rejected() throws Exception {
        adminMvc.perform(post("/api/admin/workorders/assign?id=1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_assign_valid_passes() throws Exception {
        adminMvc.perform(post("/api/admin/workorders/assign?id=1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":88}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(workOrderService).assign(1L, 1L, 88L);
    }

    // ===== admin transition：toStatus 必填 =====
    @Test
    void admin_transition_invalid_rejected() throws Exception {
        adminMvc.perform(post("/api/admin/workorders/transition?id=1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_transition_valid_passes() throws Exception {
        adminMvc.perform(post("/api/admin/workorders/transition?id=1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(workOrderService).transition(1L, 1L, "IN_PROGRESS", null);
    }
}
