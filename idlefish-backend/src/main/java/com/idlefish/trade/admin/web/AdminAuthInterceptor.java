package com.idlefish.trade.admin.web;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台鉴权拦截器：校验 /api/admin/** 的管理员令牌（服务端签发，角色由服务端决定），
 * 杜绝客户端伪造 X-Admin-Role 提权。登录接口 /api/admin/auth/** 放行。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AdminAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // R-23 生产化 · 双模取令牌（镜像用户侧 F-04）：优先 Authorization Bearer（移动端/脚本），
        // 回退到 HttpOnly Cookie admin_access_token（浏览器端，防 XSS 窃取）。
        String token = resolveToken(request);
        if (token == null) {
            throw new BizException(Code.UNAUTHORIZED);
        }
        Claims claims = jwtUtil.parse(token);
        if (!jwtUtil.isAdmin(claims)) {
            // 令牌有效但非管理员：明确无权限（区别于"未登录/令牌无效"的 20001）
            throw new BizException(Code.FORBIDDEN, "非管理员令牌，无权限访问后台");
        }
        AdminUser admin = new AdminUser();
        admin.setId(jwtUtil.getAdminId(claims));
        admin.setRole(jwtUtil.getRole(claims));
        request.setAttribute("adminUser", admin);
        return true;
    }

    /** 解析管理员令牌：Authorization Bearer 优先，其次 HttpOnly Cookie admin_access_token。 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("admin_access_token".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
