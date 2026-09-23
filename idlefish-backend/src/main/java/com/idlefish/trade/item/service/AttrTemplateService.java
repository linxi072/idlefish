package com.idlefish.trade.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.item.entity.AttrTemplate;
import com.idlefish.trade.item.mapper.AttrTemplateMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 类目属性模板服务（PRD §B1）。
 */
@Service
public class AttrTemplateService {

    private final AttrTemplateMapper attrTemplateMapper;
    private final CategoryService categoryService;

    public AttrTemplateService(AttrTemplateMapper attrTemplateMapper, CategoryService categoryService) {
        this.attrTemplateMapper = attrTemplateMapper;
        this.categoryService = categoryService;
    }

    /** 某类目下的属性模板列表。 */
    public List<AttrTemplate> listByCategory(Long categoryId) {
        return attrTemplateMapper.selectList(new LambdaQueryWrapper<AttrTemplate>()
                .eq(AttrTemplate::getCategoryId, categoryId).orderByAsc(AttrTemplate::getSort));
    }

    /** 后台新增属性模板（需叶子类目）。 */
    public Long create(Long categoryId, String name, String options, Integer required, Integer sort) {
        if (name == null || name.isBlank()) {
            throw new BizException(Code.PARAM_INVALID, "属性名必填");
        }
        categoryService.getById(categoryId);
        AttrTemplate t = new AttrTemplate();
        t.setCategoryId(categoryId);
        t.setName(name);
        t.setOptions(options);
        t.setRequired(required == null ? 0 : required);
        t.setSort(sort == null ? 0 : sort);
        attrTemplateMapper.insert(t);
        return t.getId();
    }
}
