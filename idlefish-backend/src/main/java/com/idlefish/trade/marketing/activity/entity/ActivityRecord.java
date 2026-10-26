package com.idlefish.trade.marketing.activity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 活动参与记录（ITER-F08 统一活动内核）：所有玩法共用，承载幂等键与补贴数据。
 * <p>
 * 幂等：唯一索引 {@code uk_activity_idem (activity_id, idempotency_key)} 兜底并发/重放仅生效一次（REQ-06）。
 * 补贴：{@code subsidy_fen} / {@code coupon_id} 即营销 ROI 数据源（REQ-15），供 F-14.5 BI 归因。
 * 审计：{@code extra}（JSON）留存抽奖概率快照、砍价逐刀明细等可审计信息（REQ-09）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_activity_record")
public class ActivityRecord extends BaseEntity {

    /** 记录单号（MR 前缀，幂等可读）。 */
    private String recordNo;

    private Long activityId;

    private Long userId;

    /** 玩法类型。 */
    private String kind;

    /** 玩法业务 ID：砍价会话 / 抽奖轮次 / 秒杀订单占位。 */
    private String bizId;

    /** 幂等键（客户端/服务端生成，REQ-06）。 */
    private String idempotencyKey;

    /** 记录状态：JOINED / WON / LOST / QUALIFIED(砍价成团) / PENDING_REALNAME(REQ-10) / SETTLED / CANCELLED。 */
    private String status;

    /** 成交金额 / 奖品价值（分）。 */
    private Long amountFen;

    /** 平台补贴（分）：券面额补贴或现金补贴，写入即进入 ROI 数据源（REQ-15）。 */
    private Long subsidyFen;

    /** 关联发放券（高价值/实物奖品常以券形式发放，F-10 链路）。 */
    private Long couponId;

    /** 设备指纹（风险钩子 REQ-05）。 */
    private String deviceId;

    /** 审计扩展（JSON）：抽奖概率快照 / 砍价逐刀明细等（REQ-09）。 */
    private String extra;
}
