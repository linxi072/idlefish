package com.idlefish.trade.common.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用树节点（机构树 / 菜单树复用）。
 */
@Data
public class TreeVO {

    private Long id;
    private Long parentId;
    private String name;
    private Integer sort;
    /** 业务扩展字段（如 code、icon、type、status 等），由调用方按需填充。 */
    private Map<String, Object> meta = new HashMap<>();
    private List<TreeVO> children = new ArrayList<>();
}
