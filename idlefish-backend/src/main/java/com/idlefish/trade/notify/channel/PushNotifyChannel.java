package com.idlefish.trade.notify.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idlefish.trade.common.IdlefishProperties;
import com.idlefish.trade.im.ws.WsSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时 WebSocket 推送渠道：复用 IM 的 {@link WsSessionManager} 向在线用户即时推送通知摘要。
 * 离线用户静默跳过（由站内信未读计数兜底），best-effort。本地 WS 即真实通道，无需外部凭据。
 */
@Slf4j
@Component
public class PushNotifyChannel implements NotifyChannel {

    private final WsSessionManager wsSessionManager;
    private final ObjectMapper objectMapper;
    private final IdlefishProperties props;

    public PushNotifyChannel(WsSessionManager wsSessionManager, ObjectMapper objectMapper, IdlefishProperties props) {
        this.wsSessionManager = wsSessionManager;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public ChannelType type() {
        return ChannelType.PUSH;
    }

    @Override
    public void send(NotifyMessage msg) {
        if (msg.getUserId() == null) {
            return;
        }
        try {
            if (props.getNotify() == null || !props.getNotify().isPushEnabled()) {
                return; // 推送被配置关闭
            }
            if (!wsSessionManager.isOnline(msg.getUserId())) {
                return; // 离线：交由站内信兜底
            }
            String payload = msg.getPayload() != null ? msg.getPayload() : buildPayload(msg);
            wsSessionManager.send(msg.getUserId(), payload);
            log.debug("[notify:push] 已推送 userId={} type={}", msg.getUserId(), msg.getType());
        } catch (Exception e) {
            log.warn("[notify:push] 推送失败(忽略) userId={} type={}", msg.getUserId(), msg.getType(), e);
        }
    }

    private String buildPayload(NotifyMessage msg) throws Exception {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("kind", "notification");
        env.put("type", msg.getType() == null ? null : msg.getType().getCode());
        env.put("title", msg.getTitle());
        env.put("content", msg.getContent());
        env.put("bizId", msg.getBizId());
        return objectMapper.writeValueAsString(env);
    }
}
