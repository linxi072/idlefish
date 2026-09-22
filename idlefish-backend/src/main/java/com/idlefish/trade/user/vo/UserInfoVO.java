package com.idlefish.trade.user.vo;

import lombok.Data;

/** 用户信息（对外不暴露 openid / 加密手机号）。 */
@Data
public class UserInfoVO {
    private Long userId;
    private String nickname;
    private String avatar;
    private Integer creditScore;
}
