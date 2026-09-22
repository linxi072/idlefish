package com.idlefish.trade.common.util;

import com.idlefish.trade.common.IdlefishProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 工具测试（R-02 / RK-8）：管理员令牌须携带 admin 标志，普通用户令牌不得被识别为管理员。
 */
class JwtUtilTest {

    private JwtUtil newUtil() {
        IdlefishProperties p = new IdlefishProperties();
        p.getJwt().setSecret("test-secret-key-at-least-32-bytes-long-1234567890");
        p.getJwt().setExpireSeconds(3600);
        p.getJwt().setRefreshSeconds(7200);
        return new JwtUtil(p);
    }

    @Test
    void adminTokenCarriesAdminFlag() {
        JwtUtil util = newUtil();
        String token = util.generateAdmin(1L, "SUPER");
        Claims c = util.parse(token);
        assertTrue(util.isAdmin(c), "管理员令牌应被识别为 admin");
        assertEquals("SUPER", util.getRole(c));
        assertEquals(1L, util.getAdminId(c));
    }

    @Test
    void userTokenIsNotAdmin() {
        JwtUtil util = newUtil();
        String token = util.generate(1001L, "openid_x");
        Claims c = util.parse(token);
        assertFalse(util.isAdmin(c), "普通用户令牌不得被识别为 admin（防提权）");
        assertEquals(1001L, util.getUserId(c));
        assertNotNull(util.getOpenid(c));
    }

    @Test
    void tamperedTokenRejected() {
        JwtUtil util = newUtil();
        // 1) 篡改签名：用不同密钥的解析器验签必定失败
        String token = util.generateAdmin(1L, "SUPER");
        JwtUtil wrongKey = new JwtUtil(withSecret("different-secret-key-at-least-32-bytes-long-0000000000"));
        org.junit.jupiter.api.Assertions.assertThrows(com.idlefish.trade.common.BizException.class,
                () -> wrongKey.parse(token), "签名密钥不匹配的令牌应被拒绝");

        // 2) 篡改载荷：修改 payload 段后签名必不匹配
        String[] parts = token.split("\\.");
        String tamperedPayload = (parts[1].charAt(0) == 'A' ? "B" : "A") + parts[1].substring(1);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];
        org.junit.jupiter.api.Assertions.assertThrows(com.idlefish.trade.common.BizException.class,
                () -> util.parse(tampered), "被篡改载荷的令牌应被拒绝");
    }

    private IdlefishProperties withSecret(String secret) {
        IdlefishProperties p = new IdlefishProperties();
        p.getJwt().setSecret(secret);
        p.getJwt().setExpireSeconds(3600);
        p.getJwt().setRefreshSeconds(7200);
        return p;
    }
}
