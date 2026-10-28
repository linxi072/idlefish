package com.idlefish.trade.web;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdminArgumentResolver;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.web.CurrentUserArgumentResolver;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.inspection.controller.AdminInspectionController;
import com.idlefish.trade.inspection.dto.InspectionResultDTO;
import com.idlefish.trade.inspection.service.InspectionService;
import com.idlefish.trade.local.controller.AdminLocalController;
import com.idlefish.trade.local.service.LocalDeliveryService;
import com.idlefish.trade.local.service.RecycleService;
import com.idlefish.trade.trade.controller.BargainController;
import com.idlefish.trade.trade.entity.Bargain;
import com.idlefish.trade.trade.service.BargainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证三处「DTO 已声明约束但控制器漏加 @Valid」的补接线是否真正生效：
 * 非法载荷应被 {@code @Valid} 拦截并返回 {@link Code#PARAM_INVALID}（10002）；
 * 合法载荷应放行并由服务处理（返回 SUCCESS=0）。
 * 全程 standalone MockMvc + 真实 {@code CurrentUser/CurrentAdmin} 解析器，离线可跑，不依赖 MySQL。
 */
@ExtendWith(MockitoExtension.class)
class ControllerValidationWiringTest {

    @Mock
    private BargainService bargainService;
    @Mock
    private InspectionService inspectionService;
    @Mock
    private RecycleService recycleService;
    @Mock
    private LocalDeliveryService localDeliveryService;

    private MockMvc bargainMvc;
    private MockMvc inspectionMvc;
    private MockMvc localMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        bargainMvc = MockMvcBuilders.standaloneSetup(new BargainController(bargainService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();

        inspectionMvc = MockMvcBuilders.standaloneSetup(new AdminInspectionController(inspectionService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();

        localMvc = MockMvcBuilders.standaloneSetup(new AdminLocalController(recycleService, localDeliveryService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();
    }

    // ===== BargainController.create：BargainCreateDTO 4×@NotNull =====
    @Test
    void bargain_create_invalidBody_rejected() throws Exception {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        // standalone MockMvc 不加载 @RestControllerAdvice，校验失败由 Spring 默认处理返回 400，
        // 这正是 @Valid 已启用的直接证据（真实应用会被 GlobalExceptionHandler 转为 200 + Result.fail(10002)）。
        bargainMvc.perform(post("/api/bargain/create")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bargain_create_validBody_passes() throws Exception {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        when(bargainService.create(1L, 2L, 1L, "c1", 100L)).thenReturn(new Bargain());
        bargainMvc.perform(post("/api/bargain/create")
                        .requestAttr("loginUser", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":1,\"sellerId\":2,\"convId\":\"c1\",\"offerPrice\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(bargainService).create(1L, 2L, 1L, "c1", 100L);
    }

    // ===== AdminInspectionController.result：InspectionResultDTO @NotBlank + @NotNull =====
    @Test
    void inspection_result_invalidBody_rejected() throws Exception {
        inspectionMvc.perform(post("/api/admin/inspection/result?id=1")
                        .requestAttr("adminUser", new AdminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inspection_result_validBody_passes() throws Exception {
        inspectionMvc.perform(post("/api/admin/inspection/result?id=1")
                        .requestAttr("adminUser", new AdminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inspectionNo\":\"IN-1\",\"pass\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(inspectionService).receiveResult(eq(1L), any(InspectionResultDTO.class));
    }

    // ===== AdminLocalController.recycleQuote：RecycleQuoteRequest @Min(0) =====
    @Test
    void local_quote_negativeAmount_rejected() throws Exception {
        localMvc.perform(post("/api/admin/local/recycle/quote?id=1")
                        .requestAttr("adminUser", new AdminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalAmount\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void local_quote_validBody_passes() throws Exception {
        localMvc.perform(post("/api/admin/local/recycle/quote?id=1")
                        .requestAttr("adminUser", new AdminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalAmount\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(recycleService).quote(eq(1L), eq(100L), any(), any());
    }
}
