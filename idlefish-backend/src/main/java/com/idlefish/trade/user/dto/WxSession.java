package com.idlefish.trade.user.dto;

import lombok.Data;

/**
 * 微信会话信息：由 code2session 换取。
 * Mock 模式仅填充 openid；真实模式还会填充 unionid 与 session_key（用于解密手机号等敏感数据）。
 */
@Data
public class WxSession {

    /** 用户唯一标识（微信 openid，必填） */
    private String openid;

    /** 用户在微信开放平台的唯一标识（真实模式非空；同一开放平台账号下多端一致） */
    private String unionid;

    /** 会话密钥（真实模式非空；Mock 模式为空） */
    private String sessionKey;
}
