package com.idlefish.trade.common.web;

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
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new com.idlefish.trade.common.BizException(com.idlefish.trade.common.Code.UNAUTHORIZED);
        }
        String token = header.substring(7);
        Claims claims = jwtUtil.parse(token);
        LoginUser user = new LoginUser();
        user.setUserId(jwtUtil.getUserId(claims));
        user.setOpenid(jwtUtil.getOpenid(claims));
        request.setAttribute("loginUser", user);
        return true;
    }
}
