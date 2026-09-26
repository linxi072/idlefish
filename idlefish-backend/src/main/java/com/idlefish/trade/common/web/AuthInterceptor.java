package com.idlefish.trade.common.web;

import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器（PRD P0 A1）：校验 /api/** 下的 JWT，注入 LoginUser 到 request 属性。
 * 登录、刷新、错误与静态资源等路径在 WebConfig 中排除。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 双模取令牌：优先 Authorization: Bearer（移动端/小程序），回退到 HttpOnly Cookie（access_token，浏览器端）
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            throw new BizException(Code.UNAUTHORIZED);
        }
        Claims claims = jwtUtil.parse(token);
        LoginUser user = new LoginUser();
        user.setUserId(jwtUtil.getUserId(claims));
        user.setOpenid(jwtUtil.getOpenid(claims));
        request.setAttribute("loginUser", user);
        return true;
    }

    /** 解析令牌：Authorization Bearer 优先，其次 HttpOnly Cookie access_token（F-04 JWT→Cookie）。 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie c : cookies) {
                if ("access_token".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
