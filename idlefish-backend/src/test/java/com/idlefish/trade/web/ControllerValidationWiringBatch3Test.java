package com.idlefish.trade.web;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminAuthService;
import com.idlefish.trade.admin.web.CurrentAdminArgumentResolver;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.web.CurrentUserArgumentResolver;
import com.idlefish.trade.common.web.LoginUser;
import com.idlefish.trade.datascope.controller.AdminDataScopeController;
import com.idlefish.trade.datascope.service.DataScopeService;
import com.idlefish.trade.datascope.service.TempGrantService;
import com.idlefish.trade.export.AdminExportController;
import com.idlefish.trade.export.ExportService;
import com.idlefish.trade.favorite.controller.FavoriteController;
import com.idlefish.trade.favorite.service.FavoriteService;
import com.idlefish.trade.item.controller.ItemController;
import com.idlefish.trade.item.service.ItemService;
import com.idlefish.trade.notify.controller.NotificationController;
import com.idlefish.trade.notify.service.NotificationService;
import com.idlefish.trade.system.controller.SysRoleController;
import com.idlefish.trade.system.controller.SysUserController;
import com.idlefish.trade.system.service.SysRoleService;
import com.idlefish.trade.system.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批3 接线验证：为 system×2、item×1、export×2、data-scope×3、notify×1、favorite×1 共 10 个此前漏加 @Valid 的端点
 * 验证 @Valid 已生效——非法载荷应被拦截返回 400（真实应用由 GlobalExceptionHandler 转 200+Result.fail(10002)，
 * 此处 standalone MockMvc 不加载 @RestControllerAdvice，故直接 400 即为 @Valid 启用的铁证）；
 * 合法载荷应放行并由服务处理（返回 SUCCESS=0）。
 * 全程 standalone MockMvc + 真实 CurrentUser/CurrentAdmin 解析器 + LocalValidatorFactoryBean，离线可跑，不依赖 MySQL。
 */
@ExtendWith(MockitoExtension.class)
class ControllerValidationWiringBatch3Test {

    @Mock
    private SysUserService sysUserService;
    @Mock
    private AdminAuthService adminAuthService;
    @Mock
    private SysRoleService sysRoleService;
    @Mock
    private ItemService itemService;
    @Mock
    private ExportService exportService;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private TempGrantService tempGrantService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private FavoriteService favoriteService;

    /** 构造带 id 的管理员夹具，避免控制器方法体内 admin.getId() 拆箱 NPE（仅测试用）。 */
    private AdminUser adminUser() {
        AdminUser a = new AdminUser();
        a.setId(1L);
        return a;
    }

    private MockMvc sysUserMvc;
    private MockMvc sysRoleMvc;
    private MockMvc itemMvc;
    private MockMvc exportMvc;
    private MockMvc dataScopeMvc;
    private MockMvc notifyMvc;
    private MockMvc favoriteMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        sysUserMvc = MockMvcBuilders.standaloneSetup(new SysUserController(sysUserService, adminAuthService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();

        sysRoleMvc = MockMvcBuilders.standaloneSetup(new SysRoleController(sysRoleService, adminAuthService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();

        itemMvc = MockMvcBuilders.standaloneSetup(new ItemController(itemService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();

        exportMvc = MockMvcBuilders.standaloneSetup(new AdminExportController(exportService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();

        dataScopeMvc = MockMvcBuilders.standaloneSetup(new AdminDataScopeController(dataScopeService, tempGrantService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();

        notifyMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();

        favoriteMvc = MockMvcBuilders.standaloneSetup(new FavoriteController(favoriteService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(), new CurrentAdminArgumentResolver())
                .build();
    }

    // ===== SysUserController.assignRoles（admin） =====
    @Test
    void sysUser_assignRoles_invalid_rejected() throws Exception {
        sysUserMvc.perform(post("/api/admin/system/user/assign-roles")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysUser_assignRoles_valid_passes() throws Exception {
        sysUserMvc.perform(post("/api/admin/system/user/assign-roles")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== SysRoleController.assignMenus（admin） =====
    @Test
    void sysRole_assignMenus_invalid_rejected() throws Exception {
        sysRoleMvc.perform(post("/api/admin/system/role/assign-menus")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysRole_assignMenus_valid_passes() throws Exception {
        sysRoleMvc.perform(post("/api/admin/system/role/assign-menus")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== ItemController.edit（user） =====
    @Test
    void item_edit_invalid_rejected() throws Exception {
        itemMvc.perform(put("/api/item/edit")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void item_edit_valid_passes() throws Exception {
        itemMvc.perform(put("/api/item/edit")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== AdminExportController preview/export（admin） =====
    @Test
    void export_preview_invalid_rejected() throws Exception {
        exportMvc.perform(post("/api/admin/export/preview")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void export_preview_valid_passes() throws Exception {
        exportMvc.perform(post("/api/admin/export/preview")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    @Test
    void export_export_invalid_rejected() throws Exception {
        exportMvc.perform(post("/api/admin/export/export")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void export_export_valid_passes() throws Exception {
        exportMvc.perform(post("/api/admin/export/export")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== AdminDataScopeController preview/tempGrant/migrate（admin） =====
    @Test
    void dataScope_preview_invalid_rejected() throws Exception {
        dataScopeMvc.perform(post("/api/admin/data-scope/preview")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dataScope_preview_valid_passes() throws Exception {
        dataScopeMvc.perform(post("/api/admin/data-scope/preview")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetOrgId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    @Test
    void dataScope_tempGrant_invalid_rejected() throws Exception {
        dataScopeMvc.perform(post("/api/admin/data-scope/temp-grant")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dataScope_tempGrant_valid_passes() throws Exception {
        dataScopeMvc.perform(post("/api/admin/data-scope/temp-grant")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgIds\":[1,2],\"expiredAt\":\"2026-12-31T23:59:59\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    @Test
    void dataScope_migrate_invalid_rejected() throws Exception {
        dataScopeMvc.perform(post("/api/admin/data-scope/migrate")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dataScope_migrate_valid_passes() throws Exception {
        dataScopeMvc.perform(post("/api/admin/data-scope/migrate")
                        .requestAttr("adminUser", adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromOrgId\":1,\"toOrgId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== NotificationController.markRead（user） =====
    @Test
    void notify_markRead_invalid_rejected() throws Exception {
        notifyMvc.perform(post("/api/notify/read")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notify_markRead_valid_passes() throws Exception {
        notifyMvc.perform(post("/api/notify/read")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== FavoriteController.toggle（user） =====
    @Test
    void favorite_toggle_invalid_rejected() throws Exception {
        favoriteMvc.perform(post("/api/favorite/toggle")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void favorite_toggle_valid_passes() throws Exception {
        favoriteMvc.perform(post("/api/favorite/toggle")
                        .requestAttr("loginUser", new LoginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }
}
