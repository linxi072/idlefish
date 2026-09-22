package com.idlefish.trade.user.dto;

import com.idlefish.trade.user.entity.User;
import lombok.Data;

/** 登录结果：用户 + 双令牌。 */
@Data
public class LoginResult {
    private User user;
    private String token;
    private String refreshToken;
}
