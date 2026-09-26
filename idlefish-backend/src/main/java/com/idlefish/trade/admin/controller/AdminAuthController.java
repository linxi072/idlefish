package com.idlefish.trade.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idlefish.trade.admin.entity.AdminUser;
import com.idlefish.trade.admin.mapper.AdminUserMapper;
import com.idlefish.trade.admin.vo.AdminLoginVO;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.Result;
import com.idlefish.trade.common.util.JwtUtil;
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

    public AdminAuthController(AdminUserMapper adminUserMapper, JwtUtil jwtUtil) {
        this.adminUserMapper = adminUserMapper;
        this.jwtUtil = jwtUtil;
    }

    /** 后台登录：校验账号密码，签发管理员 JWT（角色由服务端决定）。 */
    @PostMapping("/auth/login")
    public Result<AdminLoginVO> login(@RequestParam String username, @RequestParam String password) {
        AdminUser u = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));
        if (u == null || !com.idlefish.trade.common.util.PasswordUtils.matches(password, u.getPassword())) {
            throw new BizException(Code.UNAUTHORIZED, "用户名或密码错误");
        }
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException(Code.FORBIDDEN, "账号已禁用");
        }
        String token = jwtUtil.generateAdmin(u.getId(), u.getRole());
        return Result.ok(new AdminLoginVO(token, u.getRole(), u.getNickname(), u.getId()));
    }
}
