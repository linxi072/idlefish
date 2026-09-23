package com.idlefish.trade.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 类目属性模板（PRD §B1）：每个叶子类目可挂多组属性（如手机-成色/内存）。
 * options 以 JSON 数组存储，如 ["99新","95新","9成新"]。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_attr_template")
public class AttrTemplate extends BaseEntity {

    private Long categoryId;  // 关联叶子类目
    private String name;      // 属性名，如"成色"
    private String options;   // JSON 数组字符串
    private Integer required; // 0 选填 1 必填
    private Integer sort;
}
