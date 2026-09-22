package com.idlefish.trade.im.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * IM WebSocket 处理器：连接时登记会话，断开时移除。
 * 实时消息由 ImService 主动推送，客户端也可发送心跳文本。
 */
@Component
public class ImWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ImWebSocketHandler.class);

    private final WsSessionManager sessionManager;

    public ImWebSocketHandler(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long uid = (Long) session.getAttributes().get("uid");
        if (uid != null) {
            sessionManager.add(uid, session);
            log.info("[ws] user {} connected, sid={}", uid, session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 简单心跳 / 回显，保持连接
        Long uid = (Long) session.getAttributes().get("uid");
        if (uid != null && "ping".equalsIgnoreCase(message.getPayload())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long uid = (Long) session.getAttributes().get("uid");
        if (uid != null) {
            sessionManager.remove(uid, session.getId());
            log.info("[ws] user {} disconnected, sid={}", uid, session.getId());
        }
    }
}
