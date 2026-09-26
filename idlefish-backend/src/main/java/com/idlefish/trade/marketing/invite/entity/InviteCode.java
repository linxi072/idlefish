package com.idlefish.trade.marketing.invite.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.idlefish.trade.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 邀请码（F-13.4）：每位用户一个唯一邀请码，供被邀请人注册/绑定时填写。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_invite_code")
public class InviteCode extends BaseEntity {
    private Long userId;
    /** 邀请码（唯一，大小写不敏感） */
    private String code;
}
