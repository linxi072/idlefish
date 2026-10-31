package com.idlefish.trade.web;

import com.idlefish.trade.admin.batch.AdminBatchService;
import com.idlefish.trade.admin.batch.BatchResultVO;
import com.idlefish.trade.admin.batch.controller.AdminBatchController;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdminArgumentResolver;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ITER-U03 批量端点 {@code @Valid} 接线验证（standalone MockMvc，离线可跑）。
 *
 * <p>ids 非空 / 结论非空 / 确认标记非空均为业务必填，缺失由校验拦截返回 400；
 * 合法载荷放行并委托服务（code=0）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ControllerValidationWiringBatchTest {

    @Mock private AdminBatchService batchService;

    private final AdminUser admin = admin();

    private MockMvc adminMvc;

    private AdminUser admin() {
        AdminUser a = new AdminUser();
        a.setId(1L);
        return a;
    }

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        BatchResultVO empty = BatchResultVO.of(List.of(), List.of(), 1L);
        when(batchService.auditItems(any(), any(), anyList(), anyBoolean(), any())).thenReturn(empty);
        when(batchService.banUsers(any(), any(), anyList(), any(), any())).thenReturn(empty);
        adminMvc = MockMvcBuilders.standaloneSetup(new AdminBatchController(batchService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();
    }

    @Test
    void audit_without_ids_rejected() throws Exception {
        adminMvc.perform(post("/api/admin/batch/items/audit")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[],\"pass\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void audit_without_pass_rejected() throws Exception {
        adminMvc.perform(post("/api/admin/batch/items/audit")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void audit_valid_passes() throws Exception {
        adminMvc.perform(post("/api/admin/batch/items/audit")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2],\"pass\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(batchService).auditItems(any(), eq(1L), anyList(), eq(true), any());
    }

    @Test
    void preview_valid_passes() throws Exception {
        adminMvc.perform(post("/api/admin/batch/items/preview")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1],\"pass\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(batchService).previewItems(anyList());
    }

    @Test
    void ban_without_confirmed_rejected() throws Exception {
        adminMvc.perform(post("/api/admin/batch/users/ban")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1],\"status\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ban_without_status_rejected() throws Exception {
        adminMvc.perform(post("/api/admin/batch/users/ban")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1],\"confirmed\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ban_valid_passes() throws Exception {
        adminMvc.perform(post("/api/admin/batch/users/ban")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1],\"status\":1,\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(batchService).banUsers(any(), eq(1L), anyList(), eq(1), eq(true));
    }
}
