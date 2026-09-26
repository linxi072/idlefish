package com.idlefish.trade.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户积分账户（F-13.1 积分体系）：每个用户一行，惰性创建（首次产生积分时落库）。
 * balance 为可用积分；totalEarned 为累计获得积分（仅正向增加，抵扣不影响）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_point")
public class Point extends BaseEntity {

    private Long userId;          // 用户 ID（唯一）
    private Long balance;         // 可用积分
    private Long totalEarned;     // 累计获得积分

    @Version
    private Integer version;
}
