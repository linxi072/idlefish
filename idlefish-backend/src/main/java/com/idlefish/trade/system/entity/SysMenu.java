package com.idlefish.trade.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单/权限（PC 系统管理）：type 0 目录 / 1 菜单 / 2 按钮；perms 为权限标识（如 system:user:list）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sys_menu")
public class SysMenu extends BaseEntity {

    private Long parentId;
    private String name;
    private Integer type;
    private String path;
    private String component;
    private String icon;
    private String perms;
    private Integer sort;
    private Integer status;
}
