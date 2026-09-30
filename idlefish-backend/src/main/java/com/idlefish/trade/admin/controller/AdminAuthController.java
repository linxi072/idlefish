package com.idlefish.trade.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.mapper.AdminUserMapper;
import com.idlefish.trade.admin.vo.AdminLoginVO;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台认证登录（PRD §7）。
 * 校验账号密码并签发管理员 JWT，角色由服务端读取（不信任客户端）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminUserMapper adminUserMapper;
    private final JwtUtil jwtUtil;
    private final IdlefishProperties props;

    public AdminAuthController(AdminUserMapper adminUserMapper, JwtUtil jwtUtil, IdlefishProperties props) {
        this.adminUserMapper = adminUserMapper;
        this.jwtUtil = jwtUtil;
        this.props = props;
    }

    /** 后台登录：校验账号密码，签发管理员 JWT（角色由服务端决定），并下发 HttpOnly Cookie（R-23 镜像 F-04）。 */
    @PostMapping("/auth/login")
    public Result<AdminLoginVO> login(@RequestParam String username, @RequestParam String password, HttpServletResponse response) {
        AdminUser u = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));
        if (u == null || !com.idlefish.trade.common.util.PasswordUtils.matches(password, u.getPassword())) {
            throw new BizException(Code.UNAUTHORIZED, "用户名或密码错误");
        }
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException(Code.FORBIDDEN, "账号已禁用");
        }
        String token = jwtUtil.generateAdmin(u.getId(), u.getRole());
        // R-23 生产化 · 浏览器端下发 HttpOnly + SameSite=Lax Cookie（防 XSS 窃取；与用户侧 F-04 一致）。
        // 响应体仍返回 token 以兼容移动端/脚本类客户端（Bearer 头）。
        setAdminCookie(response, token);
        return Result.ok(new AdminLoginVO(token, u.getRole(), u.getNickname(), u.getId()));
    }

    /**
     * 当前管理员（D-18 路由守卫支撑）：前端初始化时携带 Bearer 令牌或 HttpOnly Cookie 调用，
     * 校验令牌有效性与管理员身份，返回当前账号信息；无效/非管理员令牌返回 401/403。
     */
    @GetMapping("/auth/me")
    public Result<AdminLoginVO> me(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            throw new BizException(Code.UNAUTHORIZED);
        }
        Claims claims = jwtUtil.parse(token);
        if (!jwtUtil.isAdmin(claims)) {
            throw new BizException(Code.FORBIDDEN, "非管理员令牌，无权限访问后台");
        }
        Long adminId = jwtUtil.getAdminId(claims);
        String role = jwtUtil.getRole(claims);
        AdminUser u = adminUserMapper.selectById(adminId);
        if (u == null || (u.getStatus() != null && u.getStatus() == 0)) {
            throw new BizException(Code.FORBIDDEN, "账号已禁用或不存在");
        }
        return Result.ok(new AdminLoginVO(token, role, u.getNickname(), adminId));
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

    /** 下发 admin_access_token HttpOnly Cookie（防 XSS 窃取，SameSite=Lax 防 CSRF 基础面）。 */
    private void setAdminCookie(HttpServletResponse response, String token) {
        boolean secure = props.getCookie().isSecure();
        StringBuilder sb = new StringBuilder();
        sb.append("admin_access_token=").append(token)
                .append("; Path=/; HttpOnly; SameSite=Lax; Max-Age=").append(props.getJwt().getExpireSeconds());
        if (secure) {
            sb.append("; Secure");
        }
        response.addHeader("Set-Cookie", sb.toString());
    }
}
