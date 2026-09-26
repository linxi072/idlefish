package com.idlefish.trade.marketing.invite;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.marketing.invite.entity.InviteCode;
import com.idlefish.trade.marketing.invite.entity.InviteRelation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 邀请关系校验纯函数单测（F-13.4，离线可跑，无 DB 依赖）。
 */
class InviteValidatorTest {

    private InviteCode code(Long userId) {
        InviteCode c = new InviteCode();
        c.setUserId(userId);
        return c;
    }

    @Test
    @DisplayName("resolveInviter：有效邀请码返回邀请人 ID")
    void resolveOk() {
        assertEquals(7L, InviteValidator.resolveInviter(code(7L), 9L));
    }

    @Test
    @DisplayName("resolveInviter：邀请码无效抛 INVITE_CODE_NOT_FOUND")
    void resolveNull() {
        BizException ex = assertThrows(BizException.class, () -> InviteValidator.resolveInviter(null, 9L));
        assertEquals(Code.INVITE_CODE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("resolveInviter：自邀自抛 INVITE_SELF")
    void resolveSelf() {
        BizException ex = assertThrows(BizException.class, () -> InviteValidator.resolveInviter(code(7L), 7L));
        assertEquals(Code.INVITE_SELF.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("assertNotBound：未绑定不抛异常")
    void notBoundOk() {
        InviteValidator.assertNotBound(null);
    }

    @Test
    @DisplayName("assertNotBound：已绑定抛 INVITE_ALREADY_BOUND")
    void alreadyBound() {
        InviteRelation r = new InviteRelation();
        BizException ex = assertThrows(BizException.class, () -> InviteValidator.assertNotBound(r));
        assertEquals(Code.INVITE_ALREADY_BOUND.getCode(), ex.getCode());
    }
}
