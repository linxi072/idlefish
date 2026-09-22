package com.idlefish.trade.im.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护 userId -> WebSocketSession 映射，用于向在线用户实时推送消息。
 * 支持多端（同一用户多个连接）。
 */
@Component
public class WsSessionManager {

    private final Map<Long, ConcurrentHashMap<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void add(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
    }

    public void remove(Long userId, String sessionId) {
        ConcurrentHashMap<String, WebSocketSession> map = sessions.get(userId);
        if (map != null) {
            map.remove(sessionId);
            if (map.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    public boolean isOnline(Long userId) {
        ConcurrentHashMap<String, WebSocketSession> map = sessions.get(userId);
        return map != null && !map.isEmpty();
    }

    /** 向指定用户所有在线连接推送文本。 */
    public void send(Long userId, String payload) {
        ConcurrentHashMap<String, WebSocketSession> map = sessions.get(userId);
        if (map == null) {
            return;
        }
        for (WebSocketSession s : map.values()) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(payload));
                } catch (IOException ignored) {
                    // 推送失败忽略，离线由未读计数兜底
                }
            }
        }
    }
}
