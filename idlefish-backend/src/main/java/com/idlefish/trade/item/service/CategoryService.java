package com.idlefish.trade.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.item.entity.Category;
import com.idlefish.trade.item.mapper.CategoryMapper;
import com.idlefish.trade.item.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 类目服务：三级类目树构建。
 * 注：生产环境类目树应缓存（Redis / 本地 Caffeine），此处每次查库构建（数据量小，可运行）。
 */
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /** 构建完整类目树（根为 parentId = 0）。 */
    public List<CategoryVO> tree() {
        List<Category> all = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
        Map<Long, CategoryVO> nodeMap = new HashMap<>();
        for (Category c : all) {
            CategoryVO vo = new CategoryVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setIcon(c.getIcon());
            vo.setLevel(c.getLevel());
            vo.setSort(c.getSort());
            vo.setIsLeaf(c.getIsLeaf());
            nodeMap.put(c.getId(), vo);
        }
        List<CategoryVO> roots = new ArrayList<>();
        for (Category c : all) {
            CategoryVO node = nodeMap.get(c.getId());
            if (c.getParentId() == null || c.getParentId() == 0L) {
                roots.add(node);
            } else {
                CategoryVO parent = nodeMap.get(c.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(node);
                }
            }
        }
        return roots;
    }

    public Category getById(Long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BizException(Code.CATEGORY_NOT_FOUND);
        }
        return c;
    }

    /** 仅取名称（聚合商品列表时用）。 */
    public String nameOf(Long id) {
        if (id == null) {
            return null;
        }
        Category c = categoryMapper.selectById(id);
        return c == null ? null : c.getName();
    }

    /** 后台新增类目（parentId=0 表示一级类目）。 */
    public Long create(Long parentId, String name, String icon, Integer level,
                       Integer sort, Integer isLeaf) {
        if (name == null || name.isBlank()) {
            throw new BizException(Code.PARAM_INVALID, "类目名称必填");
        }
        Category c = new Category();
        c.setParentId(parentId == null ? 0L : parentId);
        c.setName(name);
        c.setIcon(icon);
        c.setLevel(level == null ? 1 : level);
        c.setSort(sort == null ? 0 : sort);
        c.setIsLeaf(isLeaf == null ? 1 : isLeaf);
        categoryMapper.insert(c);
        return c.getId();
    }
}
