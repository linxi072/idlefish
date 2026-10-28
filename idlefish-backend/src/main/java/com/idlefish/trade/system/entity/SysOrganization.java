package com.idlefish.trade.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 机构/部门（PC 系统管理）：树形结构，parent_id 自指。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sys_organization")
public class SysOrganization extends BaseEntity {

    private Long parentId;
    @NotBlank(message = "机构名称不可为空")
    private String name;
    private String code;
    private Integer level;
    private Integer sort;
    private String leader;
    private String phone;
    private Integer status;
}
