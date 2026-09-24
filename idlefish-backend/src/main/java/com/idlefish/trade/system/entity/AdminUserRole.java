package com.idlefish.trade.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员 ↔ 角色 关联。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_admin_user_role")
public class AdminUserRole extends BaseEntity {

    private Long adminUserId;
    private Long roleId;
}
