package com.idlefish.trade.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色（PC 系统管理）：code 唯一，权限由关联的菜单（t_role_menu）决定。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sys_role")
public class SysRole extends BaseEntity {

    private String name;
    private String code;
    private Integer status;
    private Integer sort;
    private String remark;
}
