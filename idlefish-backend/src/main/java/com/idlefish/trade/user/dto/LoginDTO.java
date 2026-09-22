package com.idlefish.trade.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 微信登录入参：生产环境为微信 code，本地 Mock 直接以 code 作为 openid。 */
@Data
public class LoginDTO {
    @NotBlank(message = "code 不能为空")
    private String code;
}
