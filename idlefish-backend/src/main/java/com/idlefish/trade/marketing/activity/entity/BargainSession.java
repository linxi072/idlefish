package com.idlefish.trade.marketing.activity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 砍价会话（ITER-F08 / REQ-08）。
 * <p>
 * 一次砍价发起对应一条会话：记录当前价、底价、目标助力人数与已助力数。
 * 达成底价或目标人数即置 {@code QUALIFIED} 并生成「下单资格」（有效期内以协商价下单）。
 * 与统一活动内核的 {@link ActivityRecord} 一一对应（{@link #recordNo} 关联参与记录）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_bargain_session")
public class BargainSession extends BaseEntity {

    /** 会话单号（BS 前缀，幂等可读）。 */
    private String sessionNo;

    private Long activityId;

    /** 发起人（享受砍后价下单的用户）。 */
    private Long initiatorUserId;

    /** 关联内核参与记录单号（幂等/审计）。 */
    private String recordNo;

    /** 当前价（分）。 */
    private Long currentPriceFen;

    /** 底价（分）。 */
    private Long floorPriceFen;

    /** 目标助力人数。 */
    private Integer targetAssistCount;

    /** 已助力人数。 */
    private Integer assistCount;

    /** 会话状态：ONGOING / QUALIFIED / EXPIRED。 */
    private String status;

    /** 活动结束时间（逾期未成团转 EXPIRED）。 */
    private LocalDateTime expireAt;

    /** 下单资格有效期（QUALIFIED 后生效，REQ-08）。 */
    private LocalDateTime qualifyExpireAt;
}
