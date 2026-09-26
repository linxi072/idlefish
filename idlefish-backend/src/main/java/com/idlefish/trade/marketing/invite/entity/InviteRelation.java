package com.idlefish.trade.marketing.invite.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 邀请关系（F-13.4）：邀请人 → 被邀请人，含首单返券奖励状态。
 * 唯一约束 (inviter_id, invitee_id) 兜底重复绑定。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_invite_relation")
public class InviteRelation extends BaseEntity {
    private Long inviterId;
    private Long inviteeId;
    /** 首单奖励券模板 ID（可空） */
    private Long rewardCouponId;
    /** 奖励状态：0 未奖励 / 1 已奖励 */
    private Integer rewarded;
}
