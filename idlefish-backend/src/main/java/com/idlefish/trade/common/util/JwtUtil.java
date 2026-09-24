package com.idlefish.trade.common.util;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与解析登录令牌。令牌载荷携带 uid 与 openid。
 *
 * 安全约定（F-04 密钥外置）：生产环境必须通过环境变量 {@code JWT_SECRET} 注入高强度密钥（>= 32 字节），
 * 切勿使用内置 DEV 默认密钥。若仍使用默认密钥，将在启动时输出 SECURITY 告警。
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** 内置 DEV 默认密钥（仅本地演示用）。与 application.yml 中 idlefish.jwt.secret 默认值保持一致。 */
    private static final String DEV_DEFAULT_SECRET =
            "ZzVNhe08kvOb3DHdclHpMsMKXZsoPty-dOWYNiqxhNTxpmhKf7sb1wlx2FcYYG7P";

    private final SecretKey key;
    private final long expireSeconds;
    private final long refreshSeconds;

    public JwtUtil(IdlefishProperties props) {
        String secret = props.getJwt().getSecret();
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            log.warn("[SECURITY] 当前使用内置 DEV 默认 JWT 密钥，存在被伪造令牌的风险！"
                    + " 生产环境务必通过环境变量 JWT_SECRET 注入高强度密钥（>= 32 字节）。");
        }
        // 保证 HS256 所需 >= 256 bits 的密钥长度
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireSeconds = props.getJwt().getExpireSeconds();
        this.refreshSeconds = props.getJwt().getRefreshSeconds();
    }

    public String generate(Long userId, String openid) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("openid", openid)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireSeconds * 1000L))
                .signWith(key)
                .compact();
    }

    public String generateRefresh(Long userId, String openid) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("openid", openid)
                .claim("refresh", true)
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshSeconds * 1000L))
                .signWith(key)
                .compact();
    }

    /** 签发后台管理员令牌（载荷含 admin 标志与角色，角色由服务端决定，不信任客户端）。 */
    public String generateAdmin(Long adminId, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim("admin", true)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireSeconds * 1000L))
                .signWith(key)
                .compact();
    }

    public boolean isAdmin(Claims claims) {
        return Boolean.TRUE.equals(claims.get("admin", Boolean.class));
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public Long getAdminId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BizException(Code.TOKEN_EXPIRED);
        } catch (Exception e) {
            throw new BizException(Code.UNAUTHORIZED);
        }
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getOpenid(Claims claims) {
        return claims.get("openid", String.class);
    }
}
