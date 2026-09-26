package com.idlefish.trade.marketing.invite;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.marketing.invite.entity.InviteCode;
import com.idlefish.trade.marketing.invite.entity.InviteRelation;

/**
 * 邀请关系校验（纯函数，无副作用，便于离线单测）。
 */
public final class InviteValidator {

    private InviteValidator() {
    }

    /** 解析邀请人 ID；邀请码无效或自邀自抛异常。 */
    public static Long resolveInviter(InviteCode code, Long inviteeId) {
        if (code == null || code.getUserId() == null) {
            throw new BizException(Code.INVITE_CODE_NOT_FOUND, "邀请码无效");
        }
        if (inviteeId != null && code.getUserId().equals(inviteeId)) {
            throw new BizException(Code.INVITE_SELF, "不能填写自己的邀请码");
        }
        return code.getUserId();
    }

    /** 已绑定邀请关系则抛异常。 */
    public static void assertNotBound(InviteRelation existing) {
        if (existing != null) {
            throw new BizException(Code.INVITE_ALREADY_BOUND, "您已绑定邀请关系");
        }
    }
}
