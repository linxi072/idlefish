package com.idlefish.trade.user.service;

import com.idlefish.trade.user.dto.WxSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 本地 Mock 微信登录（idlefish.login.mock=true 默认）：以 code 直接作为 openid。
 * 用于沙箱/演示环境离线跑通登录与后续交易链路；
 * 生产部署请通过环境变量 IDLEFISH_LOGIN_MOCK=false 切换为真实实现。
 */
@Service
@Primary
@ConditionalOnProperty(name = "idlefish.login.mock", havingValue = "true", matchIfMissing = true)
public class MockWechatLoginServiceImpl implements WechatLoginService {

    @Override
    public WxSession code2Session(String jsCode) {
        WxSession s = new WxSession();
        s.setOpenid(jsCode);
        s.setUnionid(null);
        s.setSessionKey(null);
        return s;
    }
}
