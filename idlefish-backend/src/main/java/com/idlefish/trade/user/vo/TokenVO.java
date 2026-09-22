package com.idlefish.trade.user.vo;

import lombok.Data;

/** 刷新令牌响应。 */
@Data
public class TokenVO {
    private String token;
    private String refreshToken;
}
