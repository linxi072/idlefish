package com.idlefish.trade.user.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收货地址（PRD P0 A2）。phone 加密存储。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_address")
public class Address extends BaseEntity {

    /** 逻辑删除标记：0 未删 / 1 已删（F-04 逻辑删除；删除自动转软删，查询自动过滤）。 */
    @TableLogic
    private Integer deleted;

    private Long userId;

    private String receiverName;

    /** 手机号（加密存储） */
    private String phone;

    private String province;

    private String city;

    private String district;

    private String detail;

    /** 0 否 / 1 默认 */
    private Integer isDefault;
}
