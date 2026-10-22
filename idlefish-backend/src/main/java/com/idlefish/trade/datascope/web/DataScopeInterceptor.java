package com.idlefish.trade.datascope.web;

import com.idlefish.trade.common.util.JwtUtil;
import com.idlefish.trade.datascope.DataScopeContext;
import com.idlefish.trade.datascope.DataScopeDecision;
import com.idlefish.trade.datascope.service.DataScopeService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 数据范围上下文装配拦截器（ITER-T05 / REQ-02/09）。
 * <p>在请求入口解析管理员令牌，调用 {@link DataScopeService#resolveForAdmin} 解析可见机构决议，
 * 写入 {@link DataScopeContext} 供 SQL 过滤层（{@code DataPermissionInterceptor}）与业务网关消费。
 * 非管理员请求（无令牌/C 端）不设置上下文，SQL 过滤层自然放行全部。</p>
 * <p>自解析令牌，不依赖 {@code AdminAuthInterceptor} 的执行顺序。</p>
 */
public class DataScopeInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final DataScopeService dataScopeService;

    public DataScopeInterceptor(JwtUtil jwtUtil, DataScopeService dataScopeService) {
        this.jwtUtil = jwtUtil;
        this.dataScopeService = dataScopeService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = resolveToken(request);
        if (token == null) {
            return true;
        }
        try {
            Claims claims = jwtUtil.parse(token);
            if (!jwtUtil.isAdmin(claims)) {
                return true;
            }
            long adminId = jwtUtil.getAdminId(claims);
            DataScopeDecision d = dataScopeService.resolveForAdmin(adminId);
            DataScopeContext.set(DataScopeContext.of(
                    d.getAdminId(), d.getSelfOrgId(), d.getScope(), d.getOrgIds()));
        } catch (Exception ignore) {
            // 解析失败：不设置上下文，交由后续认证拦截器处理（SQL 层放行）
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DataScopeContext.clear();
    }

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
