package com.idlefish.trade.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品类目（三级类目树，PRD §3.1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_category")
public class Category extends BaseEntity {

    private Long parentId;   // 0 表示根类目
    private String name;
    private Integer level;   // 1 / 2 / 3
    private String icon;
    private Integer sort;
    private Integer isLeaf;  // 0 非叶子 1 叶子
}
