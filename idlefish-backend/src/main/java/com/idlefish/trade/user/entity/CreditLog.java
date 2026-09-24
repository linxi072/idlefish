package com.idlefish.trade.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 信用分变动日志（F-06）：每次重算后的增量与快照，保证信用变动可追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_credit_log")
public class CreditLog extends BaseEntity {

    private Long userId;
    private Integer delta;     // 相对上一次快照的增量
    private String reason;     // 变动原因 code（review / order / ban / risk ...）
    private Integer snapshot;  // 变动后的信用分快照
}
