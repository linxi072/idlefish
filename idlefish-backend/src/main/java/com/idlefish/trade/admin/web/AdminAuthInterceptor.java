package com.idlefish.trade.admin.web;

import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
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
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(Code.UNAUTHORIZED);
        }
        Claims claims = jwtUtil.parse(header.substring(7));
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
}
