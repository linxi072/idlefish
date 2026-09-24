package com.idlefish.trade.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色 ↔ 菜单（权限）关联。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_role_menu")
public class RoleMenu extends BaseEntity {

    private Long roleId;
    private Long menuId;
}
