package com.idlefish.trade.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 令牌对。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenPair {
    private String token;
    private String refreshToken;
}
