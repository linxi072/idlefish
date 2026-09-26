package com.idlefish.trade.marketing.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水视图（我的积分明细）。
 */
@Data
public class PointLogVO {

    private Long id;
    private Long userId;
    private String bizType;      // EARN_SIGNIN / EARN_TRADE / EARN_REVIEW / REDEEM / REDEEM_RELEASED
    private String bizId;        // 关联业务号：订单号 / 评价 ID / 日期
    private Long delta;          // 积分变动（正=获得，负=抵扣）
    private Long balanceAfter;   // 变动后余额
    private String remark;
    private String createdAt;    // 格式化时间
}
