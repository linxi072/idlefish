package com.idlefish.trade.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据字典明细（PC 系统管理）：dict_type 关联类型，dict_label 展示、dict_value 取值。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sys_dict_data")
public class SysDictData extends BaseEntity {

    @NotBlank(message = "字典类型编码不可为空")
    private String dictType;
    @NotBlank(message = "字典标签不可为空")
    private String dictLabel;
    @NotBlank(message = "字典值不可为空")
    private String dictValue;
    private Integer dictSort;
    private Integer status;
    private String remark;
}
