package com.idlefish.trade.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据字典类型（PC 系统管理）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sys_dict_type")
public class SysDictType extends BaseEntity {

    @NotBlank(message = "字典类型编码不可为空")
    private String dictType;
    @NotBlank(message = "字典类型名称不可为空")
    private String dictName;
    private Integer status;
    private String remark;
}
