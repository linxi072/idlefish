package com.idlefish.trade.item.controller;

import com.idlefish.trade.common.Result;
import com.idlefish.trade.item.service.CategoryService;
import com.idlefish.trade.item.vo.CategoryVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 类目接口（买家/游客可访问）。
 */
@RestController
@RequestMapping("/api/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 三级类目树。 */
    @GetMapping("/tree")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    /** 类目详情。 */
    @GetMapping("/{id}")
    public Result<CategoryVO> get(@PathVariable Long id) {
        return Result.ok(toVO(categoryService.getById(id)));
    }

    private CategoryVO toVO(com.idlefish.trade.item.entity.Category c) {
        CategoryVO vo = new CategoryVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setIcon(c.getIcon());
        vo.setLevel(c.getLevel());
        vo.setSort(c.getSort());
        vo.setIsLeaf(c.getIsLeaf());
        return vo;
    }
}
