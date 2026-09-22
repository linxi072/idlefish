package com.idlefish.trade.common.web;

import lombok.Data;

/**
 * 当前登录用户信息（由 AuthInterceptor 解析 JWT 后注入）。
 */
@Data
public class LoginUser {
    private Long userId;
    private String openid;
}
