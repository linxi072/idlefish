package com.idlefish.trade.marketing.activity.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 砍价助力记录（ITER-F08 / REQ-08）。
 * <p>
 * 同一用户对同一会话仅可助力一次（唯一索引 {@code uk_session_assist (session_id, assist_user_id)} 兜底）。
 * {@code amount_fen} 留存逐刀明细，供审计与防刷核查（REQ-09）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_bargain_assist")
public class BargainAssist extends BaseEntity {

    private Long sessionId;

    private String sessionNo;

    /** 助力用户（好友）。 */
    private Long assistUserId;

    /** 设备指纹（防刷，REQ-05）。 */
    private String deviceId;

    /** 本次助力砍价额（分）。 */
    private Long amountFen;
}
