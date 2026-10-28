package com.idlefish.trade.web;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminAuthService;
import com.idlefish.trade.admin.web.CurrentAdminArgumentResolver;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.im.controller.AdminImAssistantController;
import com.idlefish.trade.im.entity.ImKnowledge;
import com.idlefish.trade.im.service.ImAssistantService;
import com.idlefish.trade.im.service.ImKnowledgeService;
import com.idlefish.trade.member.controller.AdminGrowthController;
import com.idlefish.trade.member.controller.AdminMemberLevelController;
import com.idlefish.trade.member.entity.Benefit;
import com.idlefish.trade.member.entity.MemberLevel;
import com.idlefish.trade.member.service.BenefitExchangeService;
import com.idlefish.trade.member.service.GrowthDecayService;
import com.idlefish.trade.member.service.GrowthValueService;
import com.idlefish.trade.member.service.MemberLevelService;
import com.idlefish.trade.system.controller.SysDictController;
import com.idlefish.trade.system.controller.SysMenuController;
import com.idlefish.trade.system.controller.SysOrganizationController;
import com.idlefish.trade.system.controller.SysRoleController;
import com.idlefish.trade.system.entity.SysDictData;
import com.idlefish.trade.system.entity.SysDictType;
import com.idlefish.trade.system.entity.SysMenu;
import com.idlefish.trade.system.entity.SysOrganization;
import com.idlefish.trade.system.entity.SysRole;
import com.idlefish.trade.system.service.SysDictService;
import com.idlefish.trade.system.service.SysMenuService;
import com.idlefish.trade.system.service.SysOrganizationService;
import com.idlefish.trade.system.service.SysRoleService;
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
 * 批4 接线验证：为 8 个「实体当请求体」后台端点补齐 @Valid + 实体字段 @NotBlank 约束后，
 * 验证 @Valid 已生效——非法载荷（缺 DB NOT NULL 必填字段）应被拦截返回 400（真实应用由 GlobalExceptionHandler
 * 转 200 + Result.fail(10002)；此处 standalone MockMvc 不加载 @RestControllerAdvice，故直接 400 即为铁证）；
 * 合法载荷应放行并由服务处理（返回 SUCCESS=0）。
 * <p>
 * 安全子集依据：仅对「DB NOT NULL 且无默认值」字段加约束，任何现存可用客户端必然已传入这些字段
 * （否则 DB 会拒绝），故加约束不会误杀正常调用，只把潜在的 DB 500 提前为干净的 400。
 * AdminUser / SysUserController 因 t_admin_user 无 DB NOT NULL 业务列，本批排除。
 * <p>
 * 全程 standalone MockMvc + 真实 CurrentAdmin 解析器 + LocalValidatorFactoryBean，离线可跑，不依赖 MySQL。
 */
@ExtendWith(MockitoExtension.class)
class ControllerValidationWiringBatch4Test {

    @Mock private SysRoleService sysRoleService;
    @Mock private AdminAuthService adminAuthService;
    @Mock private SysMenuService sysMenuService;
    @Mock private SysOrganizationService sysOrganizationService;
    @Mock private SysDictService sysDictService;
    @Mock private MemberLevelService memberLevelService;
    @Mock private GrowthValueService growthValueService;
    @Mock private BenefitExchangeService benefitExchangeService;
    @Mock private GrowthDecayService growthDecayService;
    @Mock private ImKnowledgeService knowledgeService;
    @Mock private ImAssistantService assistantService;

    private final AdminUser admin = adminUser();

    private MockMvc sysRoleMvc;
    private MockMvc sysMenuMvc;
    private MockMvc sysOrgMvc;
    private MockMvc sysDictMvc;
    private MockMvc memberLevelMvc;
    private MockMvc growthMvc;
    private MockMvc imMvc;

    /** 构造带 id 的管理员夹具，避免控制器方法体内 admin.getId() 拆箱 NPE（仅测试用）。 */
    private AdminUser adminUser() {
        AdminUser a = new AdminUser();
        a.setId(1L);
        return a;
    }

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        sysRoleMvc = MockMvcBuilders.standaloneSetup(new SysRoleController(sysRoleService, adminAuthService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();

        sysMenuMvc = MockMvcBuilders.standaloneSetup(new SysMenuController(sysMenuService, adminAuthService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();

        sysOrgMvc = MockMvcBuilders.standaloneSetup(new SysOrganizationController(sysOrganizationService, adminAuthService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();

        sysDictMvc = MockMvcBuilders.standaloneSetup(new SysDictController(sysDictService, adminAuthService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();

        memberLevelMvc = MockMvcBuilders.standaloneSetup(new AdminMemberLevelController(memberLevelService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();

        growthMvc = MockMvcBuilders.standaloneSetup(new AdminGrowthController(growthValueService, benefitExchangeService, growthDecayService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();

        imMvc = MockMvcBuilders.standaloneSetup(new AdminImAssistantController(knowledgeService, assistantService))
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentAdminArgumentResolver())
                .build();
    }

    // ===== SysRole：name + code 必填 =====
    @Test
    void sysRole_save_invalid_rejected() throws Exception {
        sysRoleMvc.perform(post("/api/admin/system/role")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysRole_save_valid_passes() throws Exception {
        sysRoleMvc.perform(post("/api/admin/system/role")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"运营\",\"code\":\"OPERATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    @Test
    void sysRole_update_invalid_rejected() throws Exception {
        sysRoleMvc.perform(put("/api/admin/system/role/1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"运营\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysRole_update_valid_passes() throws Exception {
        sysRoleMvc.perform(put("/api/admin/system/role/1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"运营\",\"code\":\"OPERATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== SysMenu：name 必填 =====
    @Test
    void sysMenu_save_invalid_rejected() throws Exception {
        sysMenuMvc.perform(post("/api/admin/system/menu")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysMenu_save_valid_passes() throws Exception {
        sysMenuMvc.perform(post("/api/admin/system/menu")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"商品管理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    @Test
    void sysMenu_update_invalid_rejected() throws Exception {
        sysMenuMvc.perform(put("/api/admin/system/menu/1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysMenu_update_valid_passes() throws Exception {
        sysMenuMvc.perform(put("/api/admin/system/menu/1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"商品管理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== SysOrganization：name 必填 =====
    @Test
    void sysOrg_save_invalid_rejected() throws Exception {
        sysOrgMvc.perform(post("/api/admin/system/organization")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysOrg_save_valid_passes() throws Exception {
        sysOrgMvc.perform(post("/api/admin/system/organization")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"华东大区\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    @Test
    void sysOrg_update_invalid_rejected() throws Exception {
        sysOrgMvc.perform(put("/api/admin/system/organization/1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysOrg_update_valid_passes() throws Exception {
        sysOrgMvc.perform(put("/api/admin/system/organization/1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"华东大区\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== SysDictType：dictType + dictName 必填 =====
    @Test
    void sysDictType_save_invalid_rejected() throws Exception {
        sysDictMvc.perform(post("/api/admin/system/dict/type")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysDictType_save_valid_passes() throws Exception {
        sysDictMvc.perform(post("/api/admin/system/dict/type")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"YES_NO\",\"dictName\":\"是否\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    @Test
    void sysDictType_update_invalid_rejected() throws Exception {
        sysDictMvc.perform(put("/api/admin/system/dict/type/1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"YES_NO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysDictType_update_valid_passes() throws Exception {
        sysDictMvc.perform(put("/api/admin/system/dict/type/1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"YES_NO\",\"dictName\":\"是否\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== SysDictData：dictType + dictLabel + dictValue 必填 =====
    @Test
    void sysDictData_save_invalid_rejected() throws Exception {
        sysDictMvc.perform(post("/api/admin/system/dict/data")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysDictData_save_valid_passes() throws Exception {
        sysDictMvc.perform(post("/api/admin/system/dict/data")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"YES_NO\",\"dictLabel\":\"是\",\"dictValue\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    @Test
    void sysDictData_update_invalid_rejected() throws Exception {
        sysDictMvc.perform(put("/api/admin/system/dict/data/1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"YES_NO\",\"dictLabel\":\"是\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sysDictData_update_valid_passes() throws Exception {
        sysDictMvc.perform(put("/api/admin/system/dict/data/1")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"YES_NO\",\"dictLabel\":\"是\",\"dictValue\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== MemberLevel：levelCode + levelName 必填（upsert 单端点） =====
    @Test
    void memberLevel_save_invalid_rejected() throws Exception {
        memberLevelMvc.perform(post("/api/admin/member/level/save")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberLevel_save_valid_passes() throws Exception {
        memberLevelMvc.perform(post("/api/admin/member/level/save")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"levelCode\":\"L1\",\"levelName\":\"普通会员\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== Benefit：benefitCode + benefitName + benefitType 必填 =====
    @Test
    void benefit_save_invalid_rejected() throws Exception {
        growthMvc.perform(post("/api/admin/growth/benefits/save")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void benefit_save_valid_passes() throws Exception {
        growthMvc.perform(post("/api/admin/growth/benefits/save")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"benefitCode\":\"B1\",\"benefitName\":\"包邮券\",\"benefitType\":\"COUPON\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }

    // ===== ImKnowledge：intent + keywords + answerTemplate 必填 =====
    @Test
    void imKnowledge_save_invalid_rejected() throws Exception {
        imMvc.perform(post("/api/admin/im/assistant/knowledge/save")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void imKnowledge_save_valid_passes() throws Exception {
        imMvc.perform(post("/api/admin/im/assistant/knowledge/save")
                        .requestAttr("adminUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intent\":\"PRICE\",\"keywords\":\"多少钱,价格\",\"answerTemplate\":\"该商品售价 {amount}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));
    }
}
