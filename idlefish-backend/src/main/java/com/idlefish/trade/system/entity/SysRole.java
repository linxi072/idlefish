package com.idlefish.trade.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色（PC 系统管理）：code 唯一，权限由关联的菜单（t_role_menu）决定。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sys_role")
public class SysRole extends BaseEntity {

    @NotBlank(message = "角色名称不可为空")
    private String name;
    @NotBlank(message = "角色编码不可为空")
    private String code;
    private Integer status;
    private Integer sort;
    private String remark;

    /** 数据范围（ITER-T05 / REQ-02）：SELF / SELF_AND_CHILD / APPOINT / ALL；空值回落 SELF。 */
    private String dataScope = "SELF";
    /** 指定机构集合（APPOINT 时使用），逗号分隔的机构 ID。 */
    private String dataOrgIds;
}
