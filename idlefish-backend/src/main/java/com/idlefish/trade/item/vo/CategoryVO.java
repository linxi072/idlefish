package com.idlefish.trade.item.vo;

import lombok.Data;

import java.util.List;

/**
 * 类目树节点（含子节点）。
 */
@Data
public class CategoryVO {

    private Long id;
    private String name;
    private String icon;
    private Integer level;
    private Integer sort;
    private Integer isLeaf;
    private List<CategoryVO> children;
}
