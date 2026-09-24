package com.idlefish.trade.user.service;

import com.idlefish.trade.user.dto.WxSession;

/**
 * 微信登录抽象：将小程序 wx.login() 得到的 js_code 换取微信会话（openid / unionid / session_key）。
 * 遵循项目统一的「接口 + Mock（默认）+ 真实实现（按需开启）」范式：
 * - {@link MockWechatLoginServiceImpl}（idlefish.login.mock=true 默认）：以 code 直接作为 openid，离线可跑通；
 * - {@link RealWechatLoginServiceImpl}（idlefish.login.mock=false）：调微信 jscode2session 换取真实 openid。
 */
public interface WechatLoginService {

    /**
     * 用 js_code 换取微信会话信息。
     *
     * @param jsCode 小程序 wx.login() 返回的临时登录凭证 code
     * @return 微信会话（含 openid；真实模式下还含 unionid、session_key）
     */
    WxSession code2Session(String jsCode);
}
