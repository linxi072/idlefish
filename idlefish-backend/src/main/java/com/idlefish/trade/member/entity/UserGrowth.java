package com.idlefish.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户成长值账户（F-13.2）：每个用户一行（惰性创建），记录累计成长值与当前等级。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_growth")
public class UserGrowth extends BaseEntity {

    private Long userId;             // 用户 ID（唯一）
    private Integer growthValue;     // 累计成长值
    private String levelCode;        // 当前等级编码
}
