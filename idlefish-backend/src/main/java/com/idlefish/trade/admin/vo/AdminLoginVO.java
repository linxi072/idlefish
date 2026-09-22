package com.idlefish.trade.admin.vo;

import lombok.Data;

/**
 * 后台登录返回。
 */
@Data
public class AdminLoginVO {

    private String token;
    private String role;
    private String nickname;
    private Long adminId;

    public AdminLoginVO(String token, String role, String nickname, Long adminId) {
        this.token = token;
        this.role = role;
        this.nickname = nickname;
        this.adminId = adminId;
    }
}
