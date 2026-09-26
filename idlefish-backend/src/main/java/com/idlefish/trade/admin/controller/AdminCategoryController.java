package com.idlefish.trade.admin.controller;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.service.AdminService;
import com.idlefish.trade.admin.web.CurrentAdmin;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.item.service.AttrTemplateService;
import com.idlefish.trade.item.service.CategoryService;
import com.idlefish.trade.item.vo.CategoryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 类目与属性模板管理（PRD §B1）。
 * 类目增/树、属性模板增/列，所有写操作经 RBAC 权限校验与审计留痕（由 AdminService 内部完成）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminCategoryController {

    private final AdminService adminService;
    private final CategoryService categoryService;
    private final AttrTemplateService attrTemplateService;

    public AdminCategoryController(AdminService adminService, CategoryService categoryService,
                                   AttrTemplateService attrTemplateService) {
        this.adminService = adminService;
        this.categoryService = categoryService;
        this.attrTemplateService = attrTemplateService;
    }

    /** 新增类目。 */
    @PostMapping("/categories")
    public Result<Long> categories(@CurrentAdmin AdminUser admin,
                                  @RequestParam(required = false) Long parentId,
                                  @RequestParam String name,
                                  @RequestParam(required = false) String icon,
                                  @RequestParam(required = false) Integer level,
                                  @RequestParam(required = false) Integer sort,
                                  @RequestParam(required = false) Integer isLeaf) {
        return Result.ok(adminService.createCategory(admin.getRole(), admin.getId(),
                parentId, name, icon, level, sort, isLeaf));
    }

    /** 类目树（GET，供后台类目管理页）。 */
    @GetMapping("/categories")
    public Result<List<CategoryVO>> categoryTree(@CurrentAdmin AdminUser admin) {
        return Result.ok(categoryService.tree());
    }

    /** 类目属性模板列表。 */
    @GetMapping("/attr-templates")
    public Result<List<com.idlefish.trade.item.entity.AttrTemplate>> attrTemplates(@CurrentAdmin AdminUser admin,
                                                                                @RequestParam Long categoryId) {
        return Result.ok(attrTemplateService.listByCategory(categoryId));
    }

    /** 新增类目属性模板。 */
    @PostMapping("/attr-template")
    public Result<Long> attrTemplate(@CurrentAdmin AdminUser admin,
                                    @RequestParam Long categoryId,
                                    @RequestParam String name,
                                    @RequestParam(required = false) String options,
                                    @RequestParam(required = false) Integer required,
                                    @RequestParam(required = false) Integer sort) {
        return Result.ok(attrTemplateService.create(categoryId, name, options, required, sort));
    }
}
