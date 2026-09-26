package com.idlefish.trade.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 积分流水（F-13.1 积分体系）：每笔积分变动（获得/抵扣/释放）落一条。
 * bizType: EARN_SIGNIN / EARN_TRADE / EARN_REVIEW / REDEEM / REDEEM_RELEASED
 * delta 为正表示获得、为负表示抵扣；balanceAfter 为变动后余额快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_point_log")
public class PointLog extends BaseEntity {

    private Long userId;        // 用户 ID
    private String bizType;     // 业务类型
    private String bizId;       // 关联业务号：订单号 / 评价 ID / 日期
    private Long delta;         // 积分变动（正=获得，负=抵扣）
    private Long balanceAfter;  // 变动后余额
    private String remark;      // 备注
}
