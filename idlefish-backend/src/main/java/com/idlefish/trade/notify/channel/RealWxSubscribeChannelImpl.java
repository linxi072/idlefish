package com.idlefish.trade.notify.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 微信订阅消息真实推送渠道（F-14.4）：调用微信 {@code subscribeMessage.send}，复用现有 17 类 {@link NotificationType} 在关键节点触达。
 * <p>
 * - access_token 经微信 {@code cgi-bin/token} 获取并内存缓存（按过期时间提前刷新），避免每次发消息都换 token；
 * - 仅在配置 {@code idlefish.notify.subscribe.enabled=true} 且该事件已配置 templateId 时发送；
 * - 任何异常内部吞掉（best-effort），绝不向调用方抛出，避免阻断支付/订单等主业务流程。
 * - 真实接口需联网与微信凭据；凭据缺失或网络不可用时回落日志。
 */
@Slf4j
@Component
public class RealWxSubscribeChannelImpl implements NotifyChannel {

    /** 微信接口地址（无需外部配置，固定）。 */
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";
    private static final String SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=%s";
    /** token 提前 5 分钟刷新，避免临界过期。 */
    private static final long TOKEN_REFRESH_AHEAD_MS = 5 * 60 * 1000L;

    private final IdlefishProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserOpenidResolver openidResolver;

    /** 缓存的 access_token 与过期时间戳（内存，单实例；多实例可改 Redis）。 */
    private volatile String cachedToken;
    private volatile long tokenExpireAt;

    public RealWxSubscribeChannelImpl(IdlefishProperties props, RestTemplate restTemplate,
                                      ObjectMapper objectMapper, UserOpenidResolver openidResolver) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.openidResolver = openidResolver;
    }

    @Override
    public ChannelType type() {
        return ChannelType.SUBSCRIBE;
    }

    @Override
    public void send(NotifyMessage msg) {
        try {
            IdlefishProperties.Notify.Subscribe sub = props.getNotify().getSubscribe();
            if (sub == null || !sub.isEnabled()) {
                return;
            }
            String templateId = sub.getTemplates() == null ? null : sub.getTemplates().get(msg.getType().getCode());
            if (templateId == null || templateId.isBlank()) {
                // 该事件未配置订阅模板，跳过（不报错）
                return;
            }
            String openid = openidResolver.resolveOpenid(msg.getUserId());
            if (openid == null || openid.isBlank()) {
                // 用户未取得 openid（未登录过小程序），无法订阅触达，跳过
                return;
            }
            String token = resolveToken();
            if (token == null) {
                log.warn("[notify:wx-subscribe] 获取 access_token 失败，回落 userId={} type={}", msg.getUserId(), msg.getType());
                return;
            }
            Map<String, Object> body = WxSubscribeMessageBuilder.buildBody(
                    openid, templateId, "pages/index/index", msg.getTitle(), msg.getContent());
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            String resp = restTemplate.postForObject(
                    String.format(SEND_URL, token),
                    new org.springframework.http.HttpEntity<>(body, headers),
                    String.class);
            log.debug("[notify:wx-subscribe] 发送结果 userId={} type={} resp={}", msg.getUserId(), msg.getType(), resp);
        } catch (Exception e) {
            log.warn("[notify:wx-subscribe] 发送异常(回落) userId={} type={}", msg.getUserId(), msg.getType(), e);
        }
    }

    /** 获取 access_token（内存缓存 + 提前刷新；微信失败返回 null 由调用方回落）。 */
    private String resolveToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpireAt - TOKEN_REFRESH_AHEAD_MS) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && now < tokenExpireAt - TOKEN_REFRESH_AHEAD_MS) {
                return cachedToken;
            }
            try {
                String appid = props.getLogin() == null ? null : props.getLogin().getAppid();
                String secret = props.getLogin() == null ? null : props.getLogin().getSecret();
                if (appid == null || secret == null) {
                    log.warn("[notify:wx-subscribe] 微信 appid/secret 未配置，无法获取 access_token");
                    return null;
                }
                String url = String.format(TOKEN_URL, appid, secret);
                String resp = restTemplate.getForObject(url, String.class);
                JsonNode node = objectMapper.readTree(resp);
                String token = node.path("access_token").asText(null);
                if (token == null) {
                    log.warn("[notify:wx-subscribe] 微信返回无 access_token: {}", resp);
                    return null;
                }
                long expiresIn = node.path("expires_in").asLong(7200L);
                cachedToken = token;
                tokenExpireAt = now + expiresIn * 1000L;
                return token;
            } catch (Exception e) {
                log.warn("[notify:wx-subscribe] 获取 access_token 异常(回落): {}", e.getMessage());
                return null;
            }
        }
    }
}
