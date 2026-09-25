package com.idlefish.trade.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.BizException;
import com.idlefish.trade.common.Code;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.user.dto.WxSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 真实微信登录（idlefish.login.mock=false 启用）：调用微信 jscode2session 接口，
 * 用 js_code 换取用户 openid / unionid / session_key。
 * 配置缺失或微信返回错误码时抛出 {@link BizException}（登录是入口，失败即失败，不静默吞错）。
 */
@Service
public class RealWechatLoginServiceImpl implements WechatLoginService {

    private static final Logger log = LoggerFactory.getLogger(RealWechatLoginServiceImpl.class);

    private final IdlefishProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RealWechatLoginServiceImpl(IdlefishProperties props, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private IdlefishProperties.Login login() {
        return props.getLogin();
    }

    @Override
    public WxSession code2Session(String jsCode) {
        IdlefishProperties.Login cfg = login();
        if (cfg.getAppid() == null || cfg.getAppid().isBlank()
                || cfg.getSecret() == null || cfg.getSecret().isBlank()) {
            log.error("微信小程序 AppID/AppSecret 未配置，无法执行真实登录（请设置 IDLEFISH_LOGIN_APPID / IDLEFISH_LOGIN_SECRET）");
            throw new BizException(Code.WX_LOGIN_ERROR, "微信登录未配置");
        }
        String url = cfg.getJscode2sessionUrl()
                + "?appid=" + cfg.getAppid()
                + "&secret=" + cfg.getSecret()
                + "&js_code=" + jsCode
                + "&grant_type=authorization_code";
        try {
            String resp = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(resp);
            int errcode = node.path("errcode").asInt(0);
            if (errcode != 0) {
                String errmsg = node.path("errmsg").asText("");
                log.error("微信 jscode2session 返回错误 errcode={}, errmsg={}", errcode, errmsg);
                throw new BizException(Code.WX_LOGIN_ERROR, "微信登录校验失败: " + errmsg);
            }
            WxSession s = new WxSession();
            s.setOpenid(node.path("openid").asText(null));
            s.setUnionid(node.path("unionid").asText(null));
            s.setSessionKey(node.path("session_key").asText(null));
            if (s.getOpenid() == null || s.getOpenid().isBlank()) {
                throw new BizException(Code.WX_LOGIN_ERROR, "微信登录未返回 openid");
            }
            return s;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信 jscode2session 调用异常: {}", e.getMessage());
            throw new BizException(Code.WX_LOGIN_ERROR, "微信登录服务异常");
        }
    }
}
