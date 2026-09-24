package com.idlefish.trade.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据字典类型（PC 系统管理）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sys_dict_type")
public class SysDictType extends BaseEntity {

    private String dictType;
    private String dictName;
    private Integer status;
    private String remark;
}
