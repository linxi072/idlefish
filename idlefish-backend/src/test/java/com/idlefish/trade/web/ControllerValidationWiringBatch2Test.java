package com.idlefish.trade.web;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.web.CurrentAdminArgumentResolver;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.observability.MetricsRegistry;
import com.idlefish.trade.common.web.CurrentUserArgumentResolver;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.marketing.controller.AdminActivityController;
import com.idlefish.trade.marketing.activity.mapper.MarketingActivityMapper;
import com.idlefish.trade.marketing.activity.service.ActivityKernelService;
import com.idlefish.trade.marketing.activity.service.LiveSeckillService;
import com.idlefish.trade.marketing.activity.service.LotteryService;
import com.idlefish.trade.marketing.activity.service.MktBargainService;
import com.idlefish.trade.pricing.controller.AdminPriceController;
import com.idlefish.trade.pricing.controller.PriceController;
import com.idlefish.trade.pricing.service.PricingService;
import com.idlefish.trade.pricing.vo.PriceDeviationVO;
import com.idlefish.trade.pricing.vo.PriceEstimateVO;
import com.idlefish.trade.search.serving.CircuitBreaker;
import com.idlefish.trade.search.serving.InlineSearchRecommendGateway;
import com.idlefish.trade.search.serving.SearchServiceExecutor;
import com.idlefish.trade.search.serving.SearchSyncService;
import com.idlefish.trade.search.serving.SyncLagTracker;
import com.idlefish.trade.search.serving.controller.SearchRecommendServiceController;
import com.idlefish.trade.trade.controller.ActivityController;
import com.idlefish.trade.trade.entity.Activity;
import com.idlefish.trade.trade.entity.ActivityParticipant;
import com.idlefish.trade.trade.service.ActivityService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批2 接线验证：为 pricing×4、activity×2、marketing×1、search serving×2 共 9 个此前漏加 @Valid 的端点
 * 验证 @Valid 已生效——非法载荷应被拦截返回 400（真实应用由 GlobalExceptionHandler 转 200+Result.fail(10002)，
 * 此处 standalone MockMvc 不加载 @RestControllerAdvice，故直接 400 即为 @Valid 启用的铁证）；
 * 合法载荷应放行并由服务处理（返回 SUCCESS=0）。
 * 全程 standalone MockMvc + 真实 CurrentUser/CurrentAdmin 解析器 + LocalValidatorFactoryBean，离线可跑，不依赖 MySQL。
 */
@ExtendWith(MockitoExtension.class)
class ControllerValidationWiringBatch2Test {

    @Mock
    private ActivityService activityService;
    @Mock
    private PricingService pricingService;
    @Mock
    private InlineSearchRecommendGateway gateway;
    @Mock
    private SearchSyncService syncService;
    @Mock
    private SyncLagTracker lagTracker;
    @Mock
    private CircuitBreaker breaker;
    @Mock
    private SearchServiceExecutor executor;
    @Mock
    private IdlefishProperties props;
    @Mock
    private ActivityKernelService kernel;
    @Mock
    private MktBargainService bargainService;
    @Mock
    private LotteryService lotteryService;
    @Mock
    private LiveSeckillService liveSeckillService;
    @Mock
    private MarketingActivityMapper activityMapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private MetricsRegistry metrics;

    private MockMvc activityMvc;
    private MockMvc priceMvc;
    private MockMvc adminPriceMvc;
    private MockMvc searchMvc;
    private MockMvc adminActivityMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        activityMvc = MockMvcBuilders.standaloneSetup(new ActivityController(activityService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();

        priceMvc = MockMvcBuilders.standaloneSetup(new PriceController(pricingService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();

        adminPriceMvc = MockMvcBuilders.standaloneSetup(new AdminPriceController(pricingService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();

        searchMvc = MockMvcBuilders.standaloneSetup(
                        new SearchRecommendServiceController(gateway, syncService, lagTracker, breaker, executor, props))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();

        adminActivityMvc = MockMvcBuilders.standaloneSetup(
                        new AdminActivityController(kernel, bargainService, lotteryService, liveSeckillService,
                                activityMapper, objectMapper, metrics))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();
    }

    // ===== ActivityController =====
    @Test
    void activity_create_invalid_rejected() throws Exception {
        activityMvc.perform(post("/api/activity/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activity_create_valid_passes() throws Exception {
        when(activityService.create(any())).thenReturn(new Activity());
        activityMvc.perform(post("/api/activity/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":1,\"type\":\"SECKILL\",\"activityPrice\":100,\"stock\":10,"
                                + "\"startAt\":\"2026-11-01T00:00:00\",\"endAt\":\"2026-11-02T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(activityService).create(any());
    }

    @Test
    void activity_join_invalid_rejected() throws Exception {
        activityMvc.perform(post("/api/activity/join")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activity_join_valid_passes() throws Exception {
        when(activityService.join(any(), any())).thenReturn(new ActivityParticipant());
        activityMvc.perform(post("/api/activity/join")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1,\"itemId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(activityService).join(any(), any());
    }

    // ===== PriceController（用户端估价/偏离） =====
    @Test
    void price_estimate_invalid_rejected() throws Exception {
        priceMvc.perform(post("/api/price/estimate")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void price_estimate_valid_passes() throws Exception {
        when(pricingService.estimate(any(), any())).thenReturn(new PriceEstimateVO());
        priceMvc.perform(post("/api/price/estimate")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"conditionLevel\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(pricingService).estimate(any(), any());
    }

    @Test
    void price_deviation_negativePrice_rejected() throws Exception {
        priceMvc.perform(post("/api/price/deviation")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"conditionLevel\":3,\"price\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void price_deviation_valid_passes() throws Exception {
        when(pricingService.deviation(any(), any(), anyLong())).thenReturn(new PriceDeviationVO());
        priceMvc.perform(post("/api/price/deviation")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"conditionLevel\":3,\"price\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(pricingService).deviation(any(), any(), anyLong());
    }

    // ===== AdminPriceController（运营后台配置/覆盖） =====
    @Test
    void adminPrice_config_invalid_rejected() throws Exception {
        adminPriceMvc.perform(post("/api/admin/price/config")
                        .requestAttr("adminUser", new AdminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminPrice_config_valid_passes() throws Exception {
        doNothing().when(pricingService).saveConfig(any(), any(), any(), any(), any());
        adminPriceMvc.perform(post("/api/admin/price/config")
                        .requestAttr("adminUser", new AdminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"basePriceFen\":100,\"multiplier\":1.2,\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(pricingService).saveConfig(any(), any(), any(), any(), any());
    }

    @Test
    void adminPrice_override_blankReason_rejected() throws Exception {
        adminPriceMvc.perform(post("/api/admin/price/override")
                        .requestAttr("adminUser", new AdminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"multiplier\":1.5,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminPrice_override_valid_passes() throws Exception {
        doNothing().when(pricingService).override(any(), any(), any(), any());
        adminPriceMvc.perform(post("/api/admin/price/override")
                        .requestAttr("adminUser", new AdminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"multiplier\":1.5,\"reason\":\"调价\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
        verify(pricingService).override(any(), any(), any(), any());
    }

    // ===== SearchRecommendServiceController（搜推侧回传/同步） =====
    @Test
    void search_behavior_invalid_rejected() throws Exception {
        searchMvc.perform(post("/sr/api/behavior")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_syncItem_invalid_rejected() throws Exception {
        searchMvc.perform(post("/sr/api/sync/item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ===== AdminActivityController（运营活动创建，内部含私有亏损校验，此处仅验证接线） =====
    @Test
    void adminActivity_create_invalid_rejected() throws Exception {
        adminActivityMvc.perform(post("/api/admin/activity/create")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
